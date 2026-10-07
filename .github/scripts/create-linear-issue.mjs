// Linear 이슈 없이 열린 PR에 대해, PR을 미러링한 Linear 이슈를 자동 생성하고
// PR 타이틀/본문에 이슈 키를 써넣어 상호 링크한다.
// - 열릴 때 생성하므로 머지 커밋/타이틀에 이슈 번호가 남고,
//   본문의 닫힘 매직워드(Fixes)로 머지 시 Linear 네이티브 연동이 이슈를 자동 Done 처리한다.
// - 실행: GitHub Actions (linear-issue-for-pr.yml) 가 PR 필드를 env 로 넘겨 호출.
// - 의존성 없음(Node 20 global fetch 사용).
//
// env 입력: LINEAR_API_KEY, GH_TOKEN, REPO(owner/repo),
//           PR_NUMBER, PR_TITLE, PR_BODY, PR_URL, PR_AUTHOR, PR_BRANCH

const LINEAR_API = "https://api.linear.app/graphql";
const LINEAR_TEAM_ID = "ccd6db3a-5c50-470b-81ca-2c1958e7d27c"; // 100-Thieves
const ISSUE_KEY_RE = /\b[A-Z]{2,5}-\d+\b/; // Linear 이슈 키(예: MOI-123)
const LABEL_NAME = "github-pr";

// GitHub 로그인 → Linear 유저 ID (명시적 매핑)
const GITHUB_TO_LINEAR_USER = {
  bebeis: "857d617c-7c72-47e5-82b6-dbd5abf860ad", // bebe
  dbwp031: "5d569549-df47-45bd-b825-6c3a773bea83", // 이유제
  "2wndrhs": "81dba694-a824-44df-8ee0-75b2b85bacfe", // 이중곤
};

const env = process.env;
const { LINEAR_API_KEY, GH_TOKEN, REPO, PR_NUMBER, PR_TITLE, PR_URL, PR_AUTHOR, PR_BRANCH } = env;
const PR_BODY = env.PR_BODY ?? "";

function fail(msg) {
  console.error(`✖ ${msg}`);
  process.exit(1);
}

async function linear(query, variables = {}) {
  const res = await fetch(LINEAR_API, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: LINEAR_API_KEY },
    body: JSON.stringify({ query, variables }),
  });
  const json = await res.json();
  if (!res.ok || json.errors) {
    fail(`Linear API error: ${JSON.stringify(json.errors ?? res.statusText)}`);
  }
  return json.data;
}

async function github(path, method, body) {
  return fetch(`https://api.github.com/repos/${REPO}/${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${GH_TOKEN}`,
      Accept: "application/vnd.github+json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
}

async function main() {
  if (!LINEAR_API_KEY) fail("LINEAR_API_KEY 가 없습니다 (repository secret 확인).");

  // 1) 이미 이슈 키가 참조된 PR이면 skip (branch/title/body)
  if (ISSUE_KEY_RE.test(`${PR_BRANCH}\n${PR_TITLE}\n${PR_BODY}`)) {
    console.log("↷ 이미 Linear 이슈 키가 참조되어 있어 건너뜁니다.");
    return;
  }

  // 2) 멱등성: 이 PR URL 로 만든 attachment 가 있으면 skip
  const existing = await linear(
    `query($url: String!) { attachmentsForURL(url: $url) { nodes { issue { identifier } } } }`,
    { url: PR_URL },
  );
  const already = existing.attachmentsForURL?.nodes ?? [];
  if (already.length > 0) {
    console.log(`↷ 이미 연결된 이슈가 있어 건너뜁니다: ${already.map((a) => a.issue?.identifier).join(", ")}`);
    return;
  }

  // 3) 설정: In Progress(started) 상태 + 라벨(없으면 생성)
  const team = await linear(
    `query($id: String!) {
       team(id: $id) { states { nodes { id name type } } labels { nodes { id name } } }
     }`,
    { id: LINEAR_TEAM_ID },
  );
  const startedState =
    team.team.states.nodes.find((s) => s.type === "started") ??
    fail("팀에 started 타입 상태(In Progress)가 없습니다.");

  let label = team.team.labels.nodes.find((l) => l.name === LABEL_NAME);
  if (!label) {
    const created = await linear(
      `mutation($name: String!, $teamId: String!) {
         issueLabelCreate(input: { name: $name, teamId: $teamId, color: "#95a2b3" }) {
           issueLabel { id name }
         }
       }`,
      { name: LABEL_NAME, teamId: LINEAR_TEAM_ID },
    );
    label = created.issueLabelCreate.issueLabel;
  }

  const assigneeId = GITHUB_TO_LINEAR_USER[PR_AUTHOR] ?? null;
  if (!assigneeId) console.log(`ℹ 매핑되지 않은 작성자(${PR_AUTHOR}) → 미할당으로 생성합니다.`);

  // 4) 이슈 생성 (In Progress)
  const description =
    `${PR_BODY}`.trim() +
    `\n\n---\nGitHub PR: ${PR_URL} (#${PR_NUMBER}) · Author: @${PR_AUTHOR}`;
  const created = await linear(
    `mutation($input: IssueCreateInput!) {
       issueCreate(input: $input) { issue { id identifier url } }
     }`,
    {
      input: {
        teamId: LINEAR_TEAM_ID,
        title: PR_TITLE,
        description,
        stateId: startedState.id,
        labelIds: [label.id],
        ...(assigneeId ? { assigneeId } : {}),
      },
    },
  );
  const issue = created.issueCreate.issue;
  console.log(`✔ Linear 이슈 생성: ${issue.identifier} (${issue.url})`);

  // 5) PR 타이틀/본문에 키 써넣기
  //    - 타이틀: 맨 뒤에 (MOI-XXX) suffix → 머지 커밋/타이틀에 번호가 남는다
  //    - 본문: 닫힘 매직워드(Fixes) → 머지 시 Linear 네이티브 연동이 자동 Done 처리
  const newTitle = PR_TITLE.includes(issue.identifier) ? PR_TITLE : `${PR_TITLE} (${issue.identifier})`;
  const newBody = `${PR_BODY}`.trimEnd() + `\n\nFixes ${issue.identifier}`;
  const patch = await github(`pulls/${PR_NUMBER}`, "PATCH", { title: newTitle, body: newBody });
  if (!patch.ok) console.log(`ℹ PR 타이틀/본문 수정 실패(${patch.status}) — 이슈는 생성됨. 권한 확인.`);
  else console.log("✔ PR 타이틀/본문에 이슈 키 반영 (Fixes 매직워드 포함)");

  // 6) Linear 이슈에 PR attachment
  await linear(
    `mutation($issueId: String!, $url: String!, $title: String!) {
       attachmentCreate(input: { issueId: $issueId, url: $url, title: $title }) { success }
     }`,
    { issueId: issue.id, url: PR_URL, title: `GitHub PR #${PR_NUMBER}` },
  );
}

main().catch((e) => fail(e?.stack ?? String(e)));
