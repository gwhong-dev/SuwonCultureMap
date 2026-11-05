// =====================================================
//  Jenkins 파이프라인 — GitHub Actions CI(.github/workflows/ci.yml)와 같은 단계를 Jenkins 로 재구성
//  실행: 내 PC Docker 의 Jenkins (scripts\jenkins-start.cmd, 설명은 docs/jenkins.md)
//  범위: CI 만 (운영 배포는 지금처럼 GitHub Actions Deploy 가 담당 — Jenkins 에 AWS 권한을 주지 않음)
// =====================================================
pipeline {
    agent any

    options {
        timestamps()                                   // 로그 줄마다 시각
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20')) // 기록은 최근 20번만
        disableConcurrentBuilds(abortPrevious: true)   // 새 빌드가 오면 이전 빌드 취소 = ci.yml 의 cancel-in-progress
    }

    environment {
        // 빌드용 JDK 17 (jenkins/Dockerfile) — GitHub Actions 와 같은 Temurin 17. Jenkins 본체는 JDK 21
        JAVA_HOME = '/opt/jdk17'
        // Maven 라이브러리를 jenkins_home 볼륨에 보관 → 두 번째부터 빨라짐 (ci.yml 의 cache: maven)
        MAVEN_REPO = '/var/jenkins_home/.m2/repository'
        // mvnw 가 받는 Maven 본체도 볼륨에 (기본 ~/.m2/wrapper 는 컨테이너를 다시 만들면 사라짐)
        MAVEN_USER_HOME = '/var/jenkins_home/.m2'
    }

    stages {
        stage('화면 표시 규칙 시험') {
            steps {
                sh 'node --version'
                sh 'node --test "src/test/js/*.test.js"'
            }
        }

        stage('컴파일 + 테스트') {
            steps {
                sh '"$JAVA_HOME/bin/java" -version'
                sh 'chmod +x mvnw'
                sh './mvnw -B -ntp -Dmaven.repo.local="$MAVEN_REPO" verify'
            }
            post {
                // 테스트 결과를 Jenkins 화면(Test Result · 추이 그래프)에 남김 — ci.yml 의 "테스트 결과 요약"
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/TEST-*.xml'
                }
                // 실패하면 리포트 파일을 빌드에 첨부 — ci.yml 의 upload-artifact
                failure {
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'target/surefire-reports/**'
                }
            }
        }

        stage('Docker 이미지 빌드') {
            steps {
                // PC 의 Docker 로 빌드 (Jenkins 컨테이너에 docker.sock 을 연결해 둠)
                sh 'docker build -t suwonculturemap-app:jenkins .'
            }
        }

        stage('배포 파일 확인') {
            steps {
                // 가짜 값 .env — 이 작업 폴더 안에서만 쓰고 끝나면 지움 (비밀값 아님)
                sh '''
                    cat > .env <<'EOF'
SITE_DOMAIN=example.com
APP_BASE_URL=https://example.com
DB_USERNAME=ci
DB_PASSWORD=ci-password
MYSQL_ROOT_PASSWORD=ci-root-password
SWCF_API_KEY_EVENT=x
KAKAO_REST_API_KEY=x
KAKAO_ADMIN_KEY=x
KAKAO_CLIENT_SECRET=x
GOOGLE_CLIENT_ID=x
GOOGLE_CLIENT_SECRET=x
NAVER_CLIENT_ID=x
NAVER_CLIENT_SECRET=x
EOF
                    docker compose -f docker-compose.yml -f docker-compose.prod.yml config --quiet
                    bash -n deploy/setup-server.sh
                    bash -n deploy/backup-db.sh
                    bash -n deploy/deploy.sh
                '''
                // Caddyfile 은 표준 입력으로 넘김 (-v 로 폴더를 붙이면 Jenkins 컨테이너가 아닌 PC 경로로 해석됨)
                sh '''
                    docker run --rm -i -e SITE_DOMAIN=example.com caddy:2 sh -c \
                      'cat > /tmp/Caddyfile && caddy validate --config /tmp/Caddyfile --adapter caddyfile' \
                      < deploy/Caddyfile
                '''
            }
            post {
                always {
                    sh 'rm -f .env'
                }
            }
        }
    }

    post {
        success { echo '✅ CI 통과 (Jenkins)' }
        failure { echo '❌ CI 실패 — 위 단계 로그 확인' }
    }
}
