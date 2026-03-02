FROM node:lts-alpine

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

EXPOSE 5173

# Production 빌드 대신 Vite 개발 서버를 띄워 내부 프록시(/api -> api:8080)를 활성화합니다.
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173"]
