
automata {
    // Parâmetros gerais
    def version = '2.0.0'

    descriptor = 'groupId=inji,artifactId=inji-certify,version=2.0.0'
    skipHom = true
    build.agent.image = 'library/maven:3.9-eclipse-temurin-21'

    //kustomization not ready
    //gitOps.provider = 'GIT_INFRA'     
    //gitOps.namespace = 'inji'     
    //gitOps.repos = [dev: 'gitops-np/inji']

    containers.add descriptor: 'certify-service/Dockerfile', imageName: 'inji/inji-certify'
    
    //qa.sonarOpts = '-Dsonar.exclusions=**/*.java'
}

