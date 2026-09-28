# 리뷰 반영 커밋 리라이트

리뷰 수정은 원래 관심사의 커밋에 반영한다. 최신 커밋은 amend, 이전 커밋은
fixup 후 autosquash를 사용하며 임시 fixup 커밋을 원격에 남기지 않는다.

## 수정 전 기준 확보

1. 실제 PR의 head 브랜치·head·base를 조회하고 fetch한다. 로컬 브랜치가 대상과
   같은지 확인한다. main/dev에서는 리라이트 push를 하지 않는다.
2. 관측한 원격 head, 리라이트 전 로컬 head, 비교 base, 반영할 리뷰 범위를
   저장소 밖 임시 경로에 남긴다. 커밋 해시·현재 PR/CI 상태는 worklog에 적지 않는다.
3. PR head와 로컬이 같으면 시작한다. 로컬이 뒤면 깨끗한 상태에서 fast-forward,
   로컬이 앞이면 PR head에서 시작한 승인된 리뷰 변경인지 확인한다.
4. 이미 계보가 갈라졌다면 이전 원격 head에서 시작한 리라이트인지 임시 증거·
   reflog·range-diff와 리뷰 변경 범위를 함께 확인한다. 의도적 리라이트의 증거가
   없거나 다른 사람의 변경이 섞였으면 중단한다. 계보가 다르다는 이유만으로
   정상 amend를 거부하거나, 반대로 임의 divergence를 허용하지 않는다.

## 반영·검증

- 변경 경로를 선택해 stage한다. 최신 대상은 `git commit --amend --no-edit`.
  이전 대상은 `git commit --fixup <대상-커밋>` 후 알려진 base부터 autosquash한다.
  `git -c sequence.editor=true rebase -i --autosquash <비교-base>`를 쓸 수 있다.
- range-diff로 이전 커밋 묶음과 새 묶음을 비교해 리뷰 수정 외의 변경이 섞이지
  않았는지 확인한다. base도 바뀌었다면 각각의 이전/새 base에서 비교한다.
  커밋이 삭제·추가로 표시되어 짝이 잡히지 않으면 실제 tree diff와 파일 내용도
  대조한다. range-diff 명령 성공만으로 내용 일치를 판정하지 않는다.
- 커밋별 빌드·테스트 가능성을 유지하고 변경에 맞는 검증·읽기 전용 리뷰를 수행한다.
  관련 명세와 설명 자료의 갱신도 같은 작업에서 끝낸다. 실제 PR base로 파일 수를
  다시 집계하고 작업 트리가 깨끗한지 확인한다.

## Push와 최신 head 확인

사전에 확인한 원격 head를 `review_remote_head`, 대상 브랜치를 `review_branch`로
확정한 경우 다음처럼 기대값을 명시한다. 이 값은 push 직전에 새 값으로 덮어써서
lease를 통과시키기 위한 변수가 아니다.

```bash
git push --force-with-lease="refs/heads/${review_branch}:${review_remote_head}" \
  origin "HEAD:refs/heads/${review_branch}"
```

- 일반 `--force`나 기대값 없는 lease를 사용하지 않는다. 백그라운드 fetch가
  tracking ref를 갱신해도 명시한 이전 head는 바뀌지 않아야 한다.
- lease 거부는 원격 변경 신호다. fetch·비교로 새 변경을 확인하고 보존할 방법을
  판단한다. 기대값만 새로 받아 강제로 재시도하지 않는다.
- push 후 로컬 HEAD와 실제 PR head가 같은지, 그 head의 필수 체크(없으면 전체
  체크)가 성공했는지 확인한다. 이전 head의 성공으로 완료 처리하지 않는다.
- 스택의 앞 브랜치가 바뀌면 하위 브랜치의 이전 base를 기준으로 새 base에
  리베이스해 상위 커밋이 중복되지 않게 한다. 각 하위 PR도 자기 원격 head를
  사전에 확인한 lease·파일 수·검증·최신 head 확인을 따로 수행한다.
- PR 뒤 증거와 실패 사유는 저장소 밖 임시 파일 및 반환 메시지로만 남긴다.
  재시도 상한은 ship-pr의 리뷰봇 대응 2회를 따른다.
