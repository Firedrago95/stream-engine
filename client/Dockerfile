# Stage 1: Build the React application
FROM node:lts-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./ 
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: Serve the application with Nginx (or a simple http-server if preferred)
# For simplicity, let's use a small http-server for now, Nginx would be more robust for production
FROM node:lts-alpine
WORKDIR /app
COPY --from=build /app/dist .
RUN npm install -g http-server
EXPOSE 5173
CMD ["http-server", ".", "-p", "5173"]