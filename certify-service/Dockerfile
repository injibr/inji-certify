FROM registry-ctn.prevnet/library/eclipse-temurin:21-jre-alpine

ARG SOURCE
ARG COMMIT_HASH
ARG COMMIT_ID
ARG BUILD_TIME
LABEL source=${SOURCE}
LABEL commit_hash=${COMMIT_HASH}
LABEL commit_id=${COMMIT_ID}
LABEL build_time=${BUILD_TIME}

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG spring_config_label

# can be passed during Docker build as build time environment for spring profiles active
ARG active_profile

# can be passed during Docker build as build time environment for config server URL
ARG spring_config_url

# can be passed during Docker build as build time environment for glowroot
ARG is_glowroot

# can be passed during Docker build as build time environment for artifactory URL
ARG artifactory_url

# can be passed during Docker build as build time environment for hsm client zip file path
ARG hsm_client_zip_path

# environment variable to pass hsm client zip file path, at docker runtime
ENV hsm_zip_file_path=${hsm_client_zip_path}

# environment variable to pass active profile such as DEV, QA etc at docker runtime
ENV active_profile_env=${active_profile}

# environment variable to pass github branch to pickup configuration from, at docker runtime
ENV spring_config_label_env=${spring_config_label}

# environment variable to pass spring configuration url, at docker runtime
ENV spring_config_url_env=${spring_config_url}

# environment variable to pass glowroot, at docker runtime
ENV is_glowroot_env=${is_glowroot}

# environment variable to pass artifactory url, at docker runtime
ENV artifactory_url_env=${artifactory_url}

# environment variable for certify artifactory plugins to pass at docker run time
ENV enable_certify_artifactory="true"

# environment variable for downloading hsm client in certify, to pass at docker run time
# HSM is installed at build time, so this is false at runtime
ENV download_hsm_client="false"

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user=inji

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user_group=inji

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user_uid=1001

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user_gid=1001

ARG hsm_local_dir=hsm-client

ENV hsm_local_dir_name=${hsm_local_dir}

# install packages and create user
RUN apk update \
&& apk add --no-cache unzip wget ca-certificates \
&& addgroup -g ${container_user_gid} ${container_user_group} \
&& adduser -u ${container_user_uid} -G ${container_user_group} -s /bin/sh -D ${container_user}

# set working directory for the user
WORKDIR /home/${container_user}

ENV work_dir=/home/${container_user}

ARG loader_path=${work_dir}/additional_jars/

RUN mkdir -p ${loader_path}

ENV loader_path_env=${loader_path}

# Install HSM client at build time (as root, no sudo needed)
ARG hsm_zip_path=artifactory/libs-release-local/hsm/client-21.zip
RUN if [ -n "${artifactory_url}" ]; then \
    cd /tmp && \
    wget -q "${artifactory_url}/${hsm_zip_path}" -O client-21.zip && \
    FILE_NAME=client-21.zip && \
    DIR_NAME=/home/${container_user}/${hsm_local_dir} && \
    has_parent=$(zipinfo -1 "$FILE_NAME" | awk '{split($NF,a,"/");print a[1]}' | sort -u | wc -l) && \
    if test "$has_parent" -eq 1; then \
      dirname=$(zipinfo -1 "$FILE_NAME" | awk '{split($NF,a,"/");print a[1]}' | sort -u | head -n 1) && \
      unzip $FILE_NAME && mv -v $dirname $DIR_NAME; \
    else \
      mkdir -p $DIR_NAME && unzip -d $DIR_NAME $FILE_NAME; \
    fi && \
    chmod +x $DIR_NAME/install.sh && $DIR_NAME/install.sh && \
    rm -f $FILE_NAME; \
    fi

ADD certify-service/configure_start.sh configure_start.sh

RUN sed -i 's/\r$//' configure_start.sh \
&& chmod 0555 configure_start.sh

COPY ./certify-service/target/certify-service-*.jar certify-service.jar

RUN mkdir -p /home/${container_user}/config
COPY ./docker-compose/docker-compose-injistack/config/java-security-override.properties /home/${container_user}/config/java-security-override.properties

# change permissions of file inside working dir
RUN chown -R ${container_user}:${container_user} /home/${container_user}

# select container user for all tasks
USER ${container_user_uid}:${container_user_gid}

EXPOSE 8090
EXPOSE 9010

ENTRYPOINT ["./configure_start.sh"]

CMD if [ "$is_glowroot_env" = "present" ]; then \
    wget -q "${artifactory_url_env}"/artifactory/libs-release-local/io/mosip/testing/glowroot.zip ; \
    unzip glowroot.zip ; \
    rm -rf glowroot.zip ; \
    sed -i 's/<service_name>/idp-service/g' glowroot/glowroot.properties ; \
    java -jar -javaagent:glowroot/glowroot.jar -Dloader.path="${loader_path_env}" -Dspring.cloud.config.label="${spring_config_label_env}" -Dspring.profiles.active="${active_profile_env}" -Dspring.cloud.config.uri="${spring_config_url_env}" certify-service.jar ; \
    else \
    java -jar -Dloader.path="${loader_path_env}" -Dspring.cloud.config.label="${spring_config_label_env}" -Dspring.profiles.active="${active_profile_env}" -Dspring.cloud.config.uri="${spring_config_url_env}" certify-service.jar ; \
    fi
