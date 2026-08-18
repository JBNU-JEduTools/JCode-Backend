#!/usr/bin/env bash
set -euo pipefail

container="jcode-migration-test-$$"
cleanup() {
  if [[ "$container" == jcode-migration-test-* ]]; then
    docker rm -f "$container" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

docker run --rm -d --name "$container" \
  -e MARIADB_ROOT_PASSWORD=test-only \
  -e MARIADB_DATABASE=jcode \
  mariadb:11.4@sha256:67873d30a17f6a9c331f06363b2fa15f38abca415529966d67c84f87f82439fe >/dev/null

for _ in $(seq 1 30); do
  if docker exec "$container" mariadb -uroot -ptest-only -e "SELECT 1" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "$container" mariadb -uroot -ptest-only -e "SELECT 1" >/dev/null

database=(docker exec -i "$container" mariadb -uroot -ptest-only --batch --skip-column-names jcode)
"${database[@]}" < src/test/resources/db/legacy-schema.sql
for migration in src/main/resources/db/migration/V{1..5}__*.sql; do
  "${database[@]}" < "$migration"
done
"${database[@]}" < src/test/resources/db/legacy-fixture.sql
for migration in src/main/resources/db/migration/V{6..8}__*.sql; do
  "${database[@]}" < "$migration"
done

[[ "$("${database[@]}" -e "SELECT COUNT(*) FROM course WHERE environment_profile='ALGORITHM' AND resource_profile='STANDARD' AND workspace_scope='COURSE'")" == 1 ]]
[[ "$("${database[@]}" -e "SELECT COUNT(*) FROM assignment WHERE workspace_key=CONCAT('assignment-', id) AND lifecycle_status='PROVISIONING'")" == 2 ]]
[[ "$("${database[@]}" -e "SELECT COUNT(*) FROM workspace_operation WHERE action='MIGRATE_ASSIGNMENT_PATH'")" == 2 ]]
[[ "$("${database[@]}" -e "SELECT COUNT(*) FROM workspace_operation WHERE action='ARCHIVE_FINAL_SUBMISSION'")" == 2 ]]
[[ "$("${database[@]}" -e "SELECT COUNT(*) FROM jcode WHERE instance_key='1:0:false' AND lifecycle_status='READY'")" == 1 ]]
[[ "$("${database[@]}" -e "SELECT COUNT(*) FROM jcode WHERE instance_key='1:0:false' AND lifecycle_status='ARCHIVED'")" == 1 ]]

echo "MariaDB migration 검증을 통과했습니다."
