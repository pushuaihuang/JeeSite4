pipeline {
    agent {
        label 'master'
    }

    environment {
        docker_image = 'jeesite4'
        docker_container = 'iJeesite4'
    }

    parameters {
        string(name: 'branch', defaultValue: 'master', description: 'Git branch')
        choice(name: 'env', choices: ['prod', 'qa'], description: 'Environment Type')
    }

    stages{
        stage('同步源码') {
            steps {
                git url:'git@github.com:princeqjzh/JeeSite4.git', branch:'${branch}'
            }
        }

        stage('设定配置文件'){
            steps{
                sh '''
                    . ~/.bash_profile
                    
                    if [[ "${env}" == "prod" ]]; then
                        export port="8888"
                    else
                        export port="8811"
                    fi
            
                    export os_type=`uname`
                    cd ${WORKSPACE}/web/bin/docker
                    if [[ "${os_type}" == "Darwin" ]]; then
                        sed -i "" "s/mysql_ip/${mysql_docker_ip}/g" application-${env}.yml
                        sed -i "" "s/mysql_port/${mysql_port}/g" application-${env}.yml
                        sed -i "" "s/mysql_user/${mysql_user}/g" application-${env}.yml
                        sed -i "" "s/mysql_pwd/${mysql_pwd}/g" application-${env}.yml
                        sed -i "" "s/<env>/${env}/g" Dockerfile-param
                    else
                        sed -i "s/mysql_ip/${mysql_ip}/g" application-${env}.yml
                        sed -i "s/mysql_port/${mysql_port}/g" application-${env}.yml
                        sed -i "s/mysql_user/${mysql_user}/g" application-${env}.yml
                        sed -i "s/mysql_pwd/${mysql_pwd}/g" application-${env}.yml
                        sed -i "s/<env>/${env}/g" Dockerfile-param
                    fi
                '''
            }
        }

        stage('Maven 编译'){
            steps {
                sh '''
                    cd ${WORKSPACE}/root
                    mvn clean install -Dmaven.test.skip=true
                    
                    cd ${WORKSPACE}/web
                    mvn clean package spring-boot:repackage -Dmaven.test.skip=true -U
                '''
            }
        }

        stage('停止 / 删除 现有Docker Container/Image '){
            steps {
                script{
                    try{
                        sh 'docker stop $docker_container-$env'
                    }catch(exc){
                        echo 'The container $docker_container-$env does not exist'
                    }

                    try{
                        sh 'docker rm $docker_container-$env'
                    }catch(exc){
                        echo 'The container $docker_container-$env does not exist'
                    }

                    try{
                        sh 'docker rmi $docker_image-$env'
                    }catch(exc){
                        echo 'The docker image $docker_image-$env does not exist'
                    }
                }
            }
        }

        stage('生成新的Docker Image'){
            steps {
                sh '''
                    cd ${WORKSPACE}/web/bin/docker
                    rm -f web.war
                    cp ${WORKSPACE}/web/target/web.war .
                    docker build -t $docker_image-$env -f Dockerfile-param .
                '''
            }
        }

        stage('启动新Docker实例'){
            steps {
                sh '''
                    docker run -d --name $docker_container-$env -p ${port}:8980 $docker_image-$env
                '''
            }
        }
    }
}