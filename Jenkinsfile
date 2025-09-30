
automata {
    // Parâmetros gerais
    type = 'CUSTOM'
    def version = '2.0.0'

    descriptor = "groupId=inji,artifactId=inji-certify,version=${version}"
    skipHom = true

    gitOps.provider = 'GIT_INFRA'     
    gitOps.namespace = 'inji'     
    gitOps.repos = [dev: 'gitops-np/inji']

    containers.add descriptor: '-f certify-service/Dockerfile certify-service', imageName: 'inji/inji-certify'
    
    qa.sonarOpts = '-Dsonar.exclusions=**/*.java'
    
}

