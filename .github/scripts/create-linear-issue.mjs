// Linear 이슈 없이 머지된 PR에 대해, PR을 미러링한 Linear 이슈를 자동 생성하고 상호 링크한다.
// - 실행: GitHub Actions (linear-issue-from-merged-pr.yml) 가 PR 필드를 env 로 넘겨 호출.
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
const {
  LINEAR_API_KEY,
  GH_TOKEN,
  REPO,
  PR_NUMBER,
  PR_TITLE,
  PR_URL,
  PR_AUTHOR,
  PR_BRANCH,
} = env;
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

async function main() {
  if (!LINEAR_API_KEY) fail("LINEAR_API_KEY 가 없습니다 (repository secret 확인).");

  // 1) 이미 Linear 이슈가 연결/참조된 PR이면 skip
  if (ISSUE_KEY_RE.test(`${PR_BRANCH}\n${PR_TITLE}\n${PR_BODY}`)) {
    console.log("↷ 이미 Linear 이슈 키가 참조되어 있어 건너뜁니다 (branch/title/body).");
    return;
  }

  // 2) 멱등성: 이 PR URL 로 이미 만든 attachment 가 있으면 skip (재실행 대비)
  const existing = await linear(
    `query($url: String!) { attachmentsForURL(url: $url) { nodes { id issue { identifier } } } }`,
    { url: PR_URL },
  );
  const already = existing.attachmentsForURL?.nodes ?? [];
  if (already.length > 0) {
    console.log(`↷ 이미 연결된 이슈가 있어 건너뜁니다: ${already.map((a) => a.issue?.identifier).join(", ")}`);
    return;
  }

  // 3) 설정 조회: Done 상태 + 라벨(없으면 생성)
  const team = await linear(
    `query($id: String!) {
       team(id: $id) {
         states { nodes { id name type } }
         labels { nodes { id name } }
       }
     }`,
    { id: LINEAR_TEAM_ID },
  );
  const doneState =
    team.team.states.nodes.find((s) => s.type === "completed") ??
    fail("팀에 completed 타입 상태(Done)가 없습니다.");

  let label = team.team.labels.nodes.find((l) => l.name === LABEL_NAME);
  if (!label) {
    const created = await linear(
      `mutation($name: String!, $teamId: String!) {
         issueLabelCreate(input: { name: $name, teamId: $teamId, color: "#95a2b3" }) {
           success issueLabel { id name }
         }
       }`,
      { name: LABEL_NAME, teamId: LINEAR_TEAM_ID },
    );
    label = created.issueLabelCreate.issueLabel;
  }

  const assigneeId = GITHUB_TO_LINEAR_USER[PR_AUTHOR] ?? null;
  if (!assigneeId) console.log(`ℹ 매핑되지 않은 작성자(${PR_AUTHOR}) → 미할당으로 생성합니다.`);

  // 4) 이슈 생성
  const description =
    `${PR_BODY}`.trim() +
    `\n\n---\n` +
    `GitHub PR: ${PR_URL} (#${PR_NUMBER})\n` +
    `Author: @${PR_AUTHOR}\n\n` +
    `_Linear 이슈 없이 머지된 PR에 대해 자동 생성된 트래킹 이슈입니다._`;

  const created = await linear(
    `mutation($input: IssueCreateInput!) {
       issueCreate(input: $input) { success issue { id identifier url } }
     }`,
    {
      input: {
        teamId: LINEAR_TEAM_ID,
        title: PR_TITLE,
        description,
        stateId: doneState.id,
        labelIds: [label.id],
        ...(assigneeId ? { assigneeId } : {}),
      },
    },
  );
  const issue = created.issueCreate.issue;
  console.log(`✔ Linear 이슈 생성: ${issue.identifier} (${issue.url})`);

  // 5) 상호 링크: Linear 이슈에 PR attachment + GitHub PR 에 코멘트
  await linear(
    `mutation($issueId: String!, $url: String!, $title: String!) {
       attachmentCreate(input: { issueId: $issueId, url: $url, title: $title }) { success }
     }`,
    { issueId: issue.id, url: PR_URL, title: `GitHub PR #${PR_NUMBER}` },
  );

  const commentBody = `🔗 이 PR에 대한 Linear 이슈가 자동 생성되었습니다: [${issue.identifier}](${issue.url})`;
  const gh = await fetch(`https://api.github.com/repos/${REPO}/issues/${PR_NUMBER}/comments`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${GH_TOKEN}`,
      Accept: "application/vnd.github+json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ body: commentBody }),
  });
  if (!gh.ok) console.log(`ℹ PR 코멘트 실패(${gh.status}) — 이슈는 생성됨. 권한(pull-requests: write) 확인.`);
  else console.log("✔ PR 코멘트 등록 완료");
}

main().catch((e) => fail(e?.stack ?? String(e)));
