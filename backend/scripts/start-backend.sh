#!/usr/bin/env bash
set -euo pipefail

export SERVER_PORT=8087
export BUILDER_METRO_ENABLED=true
export BUILDER_METRO_DATASOURCE_URL="jdbc:mysql://test-metro-db.cyi4arp1bouk.eu-west-1.rds.amazonaws.com:3306/metro?sslMode=REQUIRED"
export BUILDER_METRO_DATASOURCE_USERNAME="mastermetro"
export BUILDER_METRO_DATASOURCE_PASSWORD="$(security find-generic-password -s "assessment-builder-metro-test" -w)"

mvn -q -f /Users/mentesme/projects/Assessmentbuilder/backend/pom.xml spring-boot:run
