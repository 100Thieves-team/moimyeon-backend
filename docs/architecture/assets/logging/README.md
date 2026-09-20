# 로깅 보고서 그림

[보고서 원문](../../logging-report.md)의 그림이다. `.mmd`를 수정한 뒤 같은 이름의 SVG를 다시 만든다. Mermaid CLI 11.15.0으로 렌더링했다. 시스템 그림은 구현한 구조, 배포 그림은 실제 AWS 적용 전 절차임을 본문에서 구분한다.

저장소 루트에서 다음 명령을 실행한다.

```bash
for diagram in system request failure deployment; do
  mmdc -i "docs/architecture/assets/logging/$diagram.mmd" \
    -o "docs/architecture/assets/logging/$diagram.svg" \
    -c docs/architecture/assets/logging/mermaid-config.json -b white
done
```
