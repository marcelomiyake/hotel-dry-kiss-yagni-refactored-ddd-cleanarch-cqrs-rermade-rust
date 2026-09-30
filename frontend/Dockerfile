FROM node:24-alpine AS build
WORKDIR /site
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --ignore-scripts
COPY frontend/ ./
RUN npm run build

FROM nginxinc/nginx-unprivileged:alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY frontend/proxy_params /etc/nginx/proxy_params
COPY --from=build /site/dist /usr/share/nginx/html
EXPOSE 8080
