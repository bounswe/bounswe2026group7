# Use a lightweight Nginx web server image
FROM nginx:alpine

# Copy all your project files into the web server's default folder
COPY . /usr/share/nginx/html

# Expose port 80 to the outside world
EXPOSE 80