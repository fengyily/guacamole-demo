#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

# Dockerfile for guacamole-client build

# Use official maven image for the build
FROM maven:3-eclipse-temurin-21 AS builder

# Set build arguments
ARG MAVEN_ARGUMENTS="-DskipTests=true -DignoreLicenseErrors=true"

# Set build environment
ENV BUILD_DIR=/tmp/guacamole-build

# Copy the entire guacamole-client source code
COPY guacamole-client/ $BUILD_DIR/

# Change to build directory and run maven build
WORKDIR $BUILD_DIR

# Build the project
RUN mvn clean package $MAVEN_ARGUMENTS

FROM maven:3-eclipse-temurin-21 AS ext-builder

# Set build arguments
ARG MAVEN_ARGUMENTS="-DskipTests=true"

# Set build environment
ENV BUILD_DIR=/tmp/guacamole-build

# Copy the entire guacamole-client source code
COPY guacamole/ $BUILD_DIR/

# Change to build directory and run maven build
WORKDIR $BUILD_DIR

# Build the project
RUN mvn clean package $MAVEN_ARGUMENTS

# Runtime stage
FROM tomcat:9-jdk21

# Install XMLStarlet for server.xml alterations
RUN apt-get update -qq \
    && apt-get install -y xmlstarlet \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /opt/guacamole

# Copy built artifacts from builder stage
COPY --from=builder /tmp/guacamole-build/guacamole/target/guacamole-*.war /usr/local/tomcat/webapps/ROOT.war

# Create extensions directory
RUN mkdir -p /opt/guacamole/extensions

# Copy only custom dynamic connection extension from ext-builder stage
COPY --from=ext-builder /tmp/guacamole-build/target/guacamole-auth-dynamic-core-*.jar /opt/guacamole/extensions/

# Create guacamole user
RUN groupadd --gid 1001 guacamole \
    && useradd --system --create-home --shell /usr/sbin/nologin --uid 1001 --gid 1001 guacamole

# Set ownership for guacamole directories
RUN chown -R guacamole:guacamole /opt/guacamole

# Set ownership for Tomcat directories that guacamole needs to write to
RUN chown -R guacamole:guacamole /usr/local/tomcat/webapps \
    && chown -R guacamole:guacamole /usr/local/tomcat/work \
    && chown -R guacamole:guacamole /usr/local/tomcat/temp \
    && chown -R guacamole:guacamole /usr/local/tomcat/logs

# Switch to guacamole user
USER guacamole

# Environment variables
ENV GUACAMOLE_HOME=/etc/guacamole

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/guacamole/ || exit 1

# Start Tomcat
CMD ["catalina.sh", "run"]