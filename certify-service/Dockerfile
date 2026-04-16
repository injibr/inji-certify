FROM eclipse-temurin:21-jre

ARG SOURCE
ARG COMMIT_HASH
ARG COMMIT_ID
ARG BUILD_TIME

LABEL source=${SOURCE}
LABEL commit_hash=${COMMIT_HASH}
LABEL commit_id=${COMMIT_ID}
LABEL build_time=${BUILD_TIME}

ARG spring_config_label
ARG active_profile
ARG spring_config_url
ARG is_glowroot
ARG artifactory_url
ARG hsm_client_zip_path

ENV hsm_zip_file_path=${hsm_client_zip_path}
ENV active_profile_env=${active_profile}
ENV spring_config_label_env=${spring_config_label}
ENV spring_config_url_env=${spring_config_url}
ENV is_glowroot_env=${is_glowroot}
ENV artifactory_url_env=${artifactory_url}
ENV enable_certify_artifactory="true"
ENV download_hsm_client="false"

ARG container_user=mosip
ARG container_user_group=mosip
ARG container_user_uid=1001
ARG container_user_gid=1001
ARG hsm_local_dir=hsm-client

ENV hsm_local_dir_name=${hsm_local_dir}

RUN apt-get update \
    && apt-get install -y unzip wget ca-certificates \
    && groupadd -g ${container_user_gid} ${container_user_group} \
    && useradd -u ${container_user_uid} -g ${container_user_group} -s /bin/bash -m -l ${container_user} \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /home/${container_user}

ENV work_dir=/tmp
ARG loader_path=/home/${container_user}/additional_jars
ENV loader_path_env=${loader_path}

RUN mkdir -p /home/${container_user}/additional_jars \
    /home/${container_user}/config

ARG hsm_zip_path=artifactory/libs-release-local/hsm/client-21.zip
RUN if [ -n "${artifactory_url}" ]; then \
      cd /tmp && \
      wget -q "${artifactory_url}/${hsm_zip_path}" -O client.zip && \
      FILE_NAME=client.zip && \
      DIR_NAME=/home/${container_user}/${hsm_local_dir} && \
      has_parent=$(zipinfo -1 "$FILE_NAME" | awk '{split($NF,a,"/");print a[1]}' | sort -u | wc -l) && \
      if test "$has_parent" -eq 1; then \
        dirname=$(zipinfo -1 "$FILE_NAME" | awk '{split($NF,a,"/");print a[1]}' | sort -u | head -n 1) && \
        unzip "$FILE_NAME" && mv -v "$dirname" "$DIR_NAME"; \
      else \
        mkdir -p "$DIR_NAME" && unzip -d "$DIR_NAME" "$FILE_NAME"; \
      fi && \
      chmod +x "$DIR_NAME/install.sh" && \
      "$DIR_NAME/install.sh" && \
      rm -f "$FILE_NAME"; \
    fi

COPY certify-service/configure_start.sh /home/${container_user}/configure_start.sh
RUN sed -i 's/\r$//' /home/${container_user}/configure_start.sh \
    && chmod 0755 /home/${container_user}/configure_start.sh

COPY ./certify-service/target/certify-service-*.jar /home/${container_user}/certify-service.jar
COPY ./docker-compose/docker-compose-injistack/config/java-security-override.properties /home/${container_user}/config/java-security-override.properties

RUN chown -R ${container_user_uid}:${container_user_gid} /home/${container_user}

USER ${container_user_uid}:${container_user_gid}

EXPOSE 8090
EXPOSE 9010

ENTRYPOINT ["/bin/bash", "/home/mosip/configure_start.sh"]

CMD if [ "$is_glowroot_env" = "present" ]; then \
      cd /tmp && \
      wget -q "${artifactory_url_env}/artifactory/libs-release-local/io/mosip/testing/glowroot.zip" -O glowroot.zip && \
      unzip glowroot.zip && \
      rm -f glowroot.zip && \
      sed -i 's/<service_name>/idp-service/g' glowroot/glowroot.properties && \
      java -jar -javaagent:/tmp/glowroot/glowroot.jar \
        -Dloader.path="${loader_path_env}" \
        -Dspring.cloud.config.label="${spring_config_label_env}" \
        -Dspring.profiles.active="${active_profile_env}" \
        -Dspring.cloud.config.uri="${spring_config_url_env}" \
        /home/mosip/certify-service.jar ; \
    else \
      java -jar \
        -Dloader.path="${loader_path_env}" \
        -Dspring.cloud.config.label="${spring_config_label_env}" \
        -Dspring.profiles.active="${active_profile_env}" \
        -Dspring.cloud.config.uri="${spring_config_url_env}" \
        /home/mosip/certify-service.jar ; \
    fi