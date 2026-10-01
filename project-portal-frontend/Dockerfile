# Frontend Dockerfile for Create React App
FROM node:20-alpine AS build
WORKDIR /app

# Copy package files
COPY package*.json ./
COPY package-lock.json ./

# Install dependencies
RUN npm ci

# Copy source code
COPY . .

# Build the React app (outputs to 'build' folder for CRA)
RUN npm run build

# Runtime stage - use nginx to serve static files
FROM nginx:alpine

# Copy built files from build stage (CRA uses 'build', not 'dist')
COPY --from=build /app/build /usr/share/nginx/html

# Copy nginx config to handle React Router client-side routing and proxy /api/*.
# As a template, ${LOGIN_UPSTREAM} etc. are substituted at container start.
COPY nginx.conf /etc/nginx/templates/default.conf.template

# Backend addresses (host:port); defaults suit a single Docker network
ENV LOGIN_UPSTREAM=login-service:8081 \
    PROJECT_UPSTREAM=project-service:8082 \
    SCAN_UPSTREAM=scan-service:8083

# Expose port 80
EXPOSE 80

# Start nginx
CMD ["nginx", "-g", "daemon off;"]