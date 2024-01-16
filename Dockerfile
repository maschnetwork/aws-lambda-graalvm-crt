FROM public.ecr.aws/amazonlinux/amazonlinux:2023

RUN yum -y update \
    && yum install -y unzip tar gzip bzip2-devel ed gcc gcc-c++ gcc-gfortran \
    less libcurl-devel openssl openssl-devel readline-devel xz-devel \
    zlib-devel glibc-static zlib-static \
    && rm -rf /var/cache/yum

# Graal VM
ENV GRAAL_VERSION 17.0.9
ENV GRAAL_FOLDERNAME graalvm-community-jdk-${GRAAL_VERSION}
ENV GRAAL_FILENAME ${GRAAL_FOLDERNAME}_linux-x64_bin.tar.gz
RUN curl -4 -L https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${GRAAL_VERSION}/${GRAAL_FILENAME} | tar -xvz
RUN mv graalvm-community-openjdk-${GRAAL_VERSION}* /usr/lib/graalvm
RUN rm -rf $GRAAL_FOLDERNAME

# Maven
ENV MVN_VERSION 3.9.6
ENV MVN_FOLDERNAME apache-maven-${MVN_VERSION}
ENV MVN_FILENAME apache-maven-${MVN_VERSION}-bin.tar.gz
RUN curl -4 -L https://archive.apache.org/dist/maven/maven-3/${MVN_VERSION}/binaries/${MVN_FILENAME} | tar -xvz
RUN mv $MVN_FOLDERNAME /usr/lib/maven
RUN rm -rf $MVN_FOLDERNAME

# AWS Lambda Builders
#RUN amazon-linux-extras enable python3.8
RUN yum clean metadata && yum -y install python3-pip
RUN pip3 install aws-lambda-builders

VOLUME /project
WORKDIR /project


RUN /usr/lib/graalvm/bin/gu install native-image
RUN ln -s /usr/lib/graalvm/bin/native-image /usr/bin/native-image
RUN ln -s /usr/lib/maven/bin/mvn /usr/bin/mvn

ENV JAVA_HOME /usr/lib/graalvm

COPY aws-crt-1.0.0-SNAPSHOT.jar .
RUN mvn install:install-file -Dfile=aws-crt-1.0.0-SNAPSHOT.jar -DgroupId=software.amazon.awssdk.crt -DartifactId=aws-crt -Dversion=1.0.0-SNAPSHOT -Dpackaging=jar -DgeneratePom=true

ENTRYPOINT ["sh"]