#!/bin/bash
set -euo pipefail
GREEN='[0;32m'; YELLOW='[1;33m'; RED='[0;31m'; NC='[0m'
log(){ printf "${YELLOW}[INFO]${NC} %s
" "$*"; }
success(){ printf "${GREEN}[OK]${NC} %s
" "$*"; }
error(){ printf "${RED}[ERROR]${NC} %s
" "$*" >&2; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/.deploy-config"

# use interactive (-i) login (-l) bash so alias expansion is ON
remote_exec() {
  ssh -tt -o IdentitiesOnly=yes -i "$SSH_KEY_PATH" "$REMOTE_SERVER"       bash -ilc "$*"
}

build() {
  cd "$SERVER_DIR"
  log "Building project..."
  ./gradlew bootJar -x test --no-build-cache
  JAR_PATH=$(ls build/libs/*.jar | grep -v plain | head -n1)
  [[ -f "$JAR_PATH" ]] || { error "Jar not produced"; exit 1; }
  JAR_NAME=$(basename "$JAR_PATH")
  success "Jar: $JAR_NAME"
}

deploy() {
  build
  log "Stopping (bs)";   remote_exec "bs || true"
  log "Deleting (bd)";   remote_exec "bd || true"
  log "Uploading ...";   scp -C -i "$SSH_KEY_PATH" "$JAR_PATH" "$REMOTE_SERVER:$REMOTE_PATH/"
  log "Starting (brs)";   remote_exec "brs"
  success "Deploy complete"
}

status()  { remote_exec "bstat"; }
restart() { remote_exec "brs"; }
logs()    { remote_exec "bl"; }

case "${1:-deploy}" in
  deploy)   deploy;;
  status)   status;;
  restart)  restart;;
  logs)     logs;;
  *) echo "Usage: $0 [deploy|status|restart|logs]";;
esac
