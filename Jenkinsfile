automata {
    // Parâmetros gerais
    type = 'CUSTOM'
    def version = '1.0.0'

    descriptor = "groupId=inji,artifactId=inji-certify,version=${version}"
    containers.add descriptor: 'Dockerfile', imageName: 'inji/inji-certify'

    qa.sonarOpts = "-Dsonar.projectKey=br.gov.dataprev.inji:inji-certify -Dsonar.projectVersion=${version} -Dsonar.sources=."
    qa.encoding = 'UTF-8'
}
