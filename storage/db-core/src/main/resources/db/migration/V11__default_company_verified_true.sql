-- company.verified 의 기본값을 TRUE 로 뒤집는다.
--
-- V2 가 컬럼을 추가할 때 "이후 크롤러 적재분과 함께 검증 완료로 올리는 것은 별도 운영 절차로 다룬다"고
-- 미뤘고, V7 은 그 시점의 기존 행만 백필했다. 그래서 V7 이후 크롤러가 넣은 회사는 DEFAULT FALSE 로
-- 들어와 회사 검색에 뜨지 않는다. 백필을 또 넣으면 다음 적재분에서 같은 일이 반복되므로 기본값을 바꾼다.
--
-- 앱이 만드는 행은 영향이 없다. JPA 가 INSERT 에 값을 항상 명시하므로 여전히 FALSE 로 들어가고,
-- V2 주석의 "앱 등록분은 FALSE" 불변식이 유지된다. 컬럼을 생략하는 크롤러 INSERT 에만 적용된다.
--
-- 크롤러가 verified 를 명시적으로 FALSE 로 넣고 있다면 이 변경만으로는 부족하다.
-- 그 경우 크롤러 적재 로직을 함께 고쳐야 한다.
ALTER TABLE company
    ALTER COLUMN verified SET DEFAULT TRUE;

-- V7 이후 적재되어 아직 FALSE 로 남아 있는 크롤러 회사를 정리한다.
-- 조건부 UPDATE 라 재실행해도 결과가 같다.
UPDATE company
SET verified = TRUE
WHERE created_by_member_id IS NULL
  AND verified = FALSE;
