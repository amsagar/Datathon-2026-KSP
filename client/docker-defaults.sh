#!/bin/sh
set -eu

BASE_URL=${BASE_URL:-"http://localhost:8080"}

# nginx needs an explicit resolver for the request-time DNS of the gateway.
# Read the nameserver(s) the container was given (kube-dns in the cluster);
# bracket IPv6 addresses as nginx requires.
RESOLVERS=$(awk '$1=="nameserver"{ if ($2 ~ /:/) printf "[%s] ", $2; else printf "%s ", $2 }' /etc/resolv.conf)
RESOLVERS=$(echo "$RESOLVERS" | sed 's/[[:space:]]*$//')
RESOLVERS=${RESOLVERS:-"127.0.0.11"}

echo "Proxying /api/* to ${BASE_URL} (resolver: ${RESOLVERS})"

# Empty streamApiBase → browser uses same-origin /api (nginx proxy), avoiding CORS.
RUNTIME_CONFIG="/usr/share/nginx/html/runtime-config.js"
printf '%s\n' \
  "window.__RUNTIME_CONFIG__={streamApiBase:\"\"};" \
  > "${RUNTIME_CONFIG}"
echo "SSE/chat via same-origin /api proxy (${RUNTIME_CONFIG})"

# sed to /tmp then write back: avoids needing write permission on /etc/nginx
# (the dir is root-owned; only the file is made writable for the non-root UID).
sed -e "s|PROXY_PASS_URL|${BASE_URL}|g" \
    -e "s|RESOLVER_PLACEHOLDER|${RESOLVERS}|g" \
    /etc/nginx/nginx.conf > /tmp/nginx.conf.tmp \
    && cat /tmp/nginx.conf.tmp > /etc/nginx/nginx.conf

rm -f /tmp/nginx.conf.tmp
