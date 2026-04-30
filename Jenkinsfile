
automata {
    // Parâmetros gerais
    //def version = '2.0.0'
    //descriptor = 'groupId=inji,artifactId=inji-certify,version=2.0.0'
    
    skipHom = true
    build.agent.image = 'library/maven:3.9-eclipse-temurin-21'

    gitOps.provider = 'GIT_INFRA'
    gitOps.engine = 'HELM'
    gitOps.repos = [
        dev: 'gitops-np/credenciais-verificaveis',
        //hom: 'gitops-np/inji',
        //prd: 'gitops-p/fth-ap2145-jano/ctn-121',
    ]

    //Dependency check
    artifacts.add file: 'certify-service/target/certify-service-${version}.jar'
    
    
    containers.add descriptor: 'certify-service/Dockerfile', imageName: 'inji/inji-certify',tagKey:'injiCertify.image.tag'
 
    build.opts = "-Dgpg.skip=true -Dmaven.javadoc.skip=true"
    
   
}

