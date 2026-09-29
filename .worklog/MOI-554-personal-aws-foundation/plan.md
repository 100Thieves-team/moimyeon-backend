# MOI-554 개인 AWS Organizations Terraform 준비

- [ ] 기존 팀 계정 Terraform과 격리된 독립 루트의 범위 확정
- [ ] Organizations, OU, SCP 구성을 작성하고 Identity Center 후속 연결 지점을 문서화
- [ ] `terraform fmt -check`와 `terraform validate` 실행
- [ ] 실제 개인 계정에서의 plan 검토와 단계적 적용은 별도 진행

현재 요청은 향후 이관을 위한 코드 준비다. AWS 자원 변경이나 `terraform apply`는 이 작업 범위에 없다.

로컬 검증: `terraform fmt -check -recursive`, `terraform validate`, 기존 Terraform 설정 계약 검사, `./gradlew test ktlintCheck` 통과. 개인 계정 자격 증명이 없으므로 원격 plan은 실행하지 않았다. 체크박스는 워크플로의 승인 표시이므로 이 실행 결과만으로 체크하지 않는다.
