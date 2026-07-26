# Stage 1: build the React app
FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build

# Stage 2: serve with nginx (pod runs as a non-root UID, runAsUser: 1000)
FROM nginx:1.27-alpine

# Replace the stock config with our own full nginx.conf.
RUN rm -f /etc/nginx/conf.d/default.conf /etc/nginx/nginx.conf
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /app/build /usr/share/nginx/html
COPY docker-defaults.sh /docker-defaults.sh

# Make every path nginx and the entrypoint write to usable by any non-root UID:
# the /tmp temp tree, cache/log dirs, and the config file rewritten at startup.
RUN mkdir -p /tmp/client_temp /tmp/proxy_temp /tmp/fastcgi_temp \
        /tmp/uwsgi_temp /tmp/scgi_temp \
    && chmod -R 777 /tmp/client_temp /tmp/proxy_temp /tmp/fastcgi_temp \
        /tmp/uwsgi_temp /tmp/scgi_temp /var/cache/nginx /var/log/nginx \
    && chmod 666 /etc/nginx/nginx.conf /usr/share/nginx/html/runtime-config.js \
    && chmod +x /docker-defaults.sh

EXPOSE 8080

# Substitute BASE_URL into the proxy target, then hand off to nginx.
CMD ["/bin/sh", "-c", "/docker-defaults.sh && exec nginx -g 'daemon off;'"]
