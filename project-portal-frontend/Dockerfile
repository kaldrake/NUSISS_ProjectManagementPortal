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

# Expose port 80
EXPOSE 80

# Start nginx
CMD ["nginx", "-g", "daemon off;"]