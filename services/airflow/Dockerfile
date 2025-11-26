# services/airflow/Dockerfile

FROM apache/airflow:2.8.0-python3.9

USER root

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    gcc g++ curl git openjdk-17-jre-headless procps \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
ENV PATH=$PATH:$JAVA_HOME/bin

COPY entrypoint.sh /opt/airflow/entrypoint.sh
RUN chmod +x /opt/airflow/entrypoint.sh

USER airflow

ENV AIRFLOW__CORE__EXECUTOR=SequentialExecutor \
    AIRFLOW__DATABASE__SQL_ALCHEMY_CONN=sqlite:////opt/airflow/airflow.db \
    AIRFLOW__CORE__LOAD_EXAMPLES=false

RUN mkdir -p /opt/airflow/dags /opt/airflow/logs /opt/airflow/jobs

COPY requirements.txt /tmp/
RUN pip install -r /tmp/requirements.txt

COPY dags/ /opt/airflow/dags/
COPY jobs/ /opt/airflow/jobs/

EXPOSE 8080

ENTRYPOINT ["/opt/airflow/entrypoint.sh"]