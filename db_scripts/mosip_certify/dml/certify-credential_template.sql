-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------

INSERT INTO certify.credential_template (context, credential_type, template, cr_dtimes, upd_dtimes) VALUES ('https://www.w3.org/2018/credentials/v1', 'FarmerCredential,VerifiableCredential', '{
     "@context": [
         "https://www.w3.org/2018/credentials/v1",
         "https://piyush7034.github.io/my-files/farmer.json",
         "https://w3id.org/security/suites/ed25519-2020/v1"
     ],
     "issuer": "${_issuer}",
     "type": [
         "VerifiableCredential",
         "FarmerCredential"
     ],
     "issuanceDate": "${validFrom}",
     "expirationDate": "${validUntil}",
     "credentialSubject": {
         "id": "${_holderId}",
         "fullName": "${fullName}",
         "mobileNumber": "${mobileNumber}",
         "dateOfBirth": "${dateOfBirth}",
         "gender": "${gender}",
         "state": "${state}",
         "district": "${district}",
         "villageOrTown": "${villageOrTown}",
         "postalCode": "${postalCode}",
         "landArea": "${landArea}",
         "landOwnershipType": "${landOwnershipType}",
         "primaryCropType": "${primaryCropType}",
         "secondaryCropType": "${secondaryCropType}",
         "face": "${face}",
         "farmerID": "${farmerID}"
     }
}
', '2024-10-24 12:32:38.065994', NULL);

INSERT INTO certify.credential_template
(context, credential_type, "template", cr_dtimes, upd_dtimes)
VALUES('https://www.w3.org/2018/credentials/v1', 'CARReceipt,VerifiableCredential', '{"@context": ["https://www.w3.org/2018/credentials/v1"],"issuer": "${_issuer}","type": ["VerifiableCredential","CARReceipt"],"issuanceDate": "${validFrom}","expirationDate": "${validUntil}","credentialSubject": {"codigoImovel": "${codigoImovel}","dataCadastro": "${dataCadastro}","nomeImovel": "${nomeImovel}","municipio": "${municipio}","unidadefederativa": "${unidadefederativa}","coordenadaImovelX": "${coordenadaImovelX}","coordenadaImovelY": "${coordenadaImovelY}","areaTotalImovel": "${areaTotalImovel}","moduloFiscal": "${moduloFiscal}","protocolo": "${protocolo}","areaLiquidaImovel": "${areaLiquidaImovel}","areaPreservacaoPermanente": "${areaPreservacaoPermanente}","areaReservaLegal": "${areaReservaLegal}","areaUsoRestrito": "${areaUsoRestrito}","areaConsolidada": "${areaConsolidada}","areaRemanescenteVegetacaoNativa": "${areaRemanescenteVegetacaoNativa}","cpfCnpj": "${cpfCnpj}","informacoesAdicionais": "${informacoesAdicionais}","geoImovel": "${geoImovel}","areaServidaoAdministrativa": "${areaServidaoAdministrativa}","nomeProprietario": "${nomeProprietario}"}}', '2024-10-24 12:32:38.065', NULL);

INSERT INTO certify.credential_template
(context, credential_type, "template", cr_dtimes, upd_dtimes)
VALUES('https://www.w3.org/2018/credentials/v1', 'CARDocument,VerifiableCredential', '{"@context": ["https://www.w3.org/2018/credentials/v1"],"issuer": "${_issuer}","type": ["VerifiableCredential","CARDocument"],"issuanceDate": "${validFrom}","expirationDate": "${validUntil}","credentialSubject": {"id": "${_holderId}","situacaoImovel": "${situacaoImovel}","codigoImovel": "${codigoImovel}","descricaoEtapaCadastro": "${descricaoEtapaCadastro}","areaTotalImovel": "${areaTotalImovel}","quantidadeModulosFiscais": "${quantidadeModulosFiscais}","dataCadastro": "${dataCadastro}","dataUltimaAtualizacaoCadastro": "${dataUltimaAtualizacaoCadastro}","municipio": "${municipio}","unidadeFederativa": "${unidadeFederativa}","coordenadaImovelX": "${coordenadaImovelX}","coordenadaImovelY": "${coordenadaImovelY}","areaRemanescenteVegetacaoNativa": "${areaRemanescenteVegetacaoNativa}","areaConsolidada": "${areaConsolidada}","areaServidaoAdministrativa": "${areaServidaoAdministrativa}","situacaoReservaLegal": "${situacaoReservaLegal}","areaReservaLegalAverbadaDocumental": "${areaReservaLegalAverbadaDocumental}","areaReservaLegalAverbada": "${areaReservaLegalAverbada}","areaReservaLegalAprovadaNaoAverbada": "${areaReservaLegalAprovadaNaoAverbada}","areaReservaLegalProposta": "${areaReservaLegalProposta}","areaReservaLegalDeclaradaProprietarioPossuidor": "${areaReservaLegalDeclaradaProprietarioPossuidor}","areaPreservacaoPermanente": "${areaPreservacaoPermanente}","areaPreservacaoPermanenteAreaRuralConsolida": "${areaPreservacaoPermanenteAreaRuralConsolida}","areaPreservacaoPermanenteAreaRemanescenteVegetacaoNativa": "${areaPreservacaoPermanenteAreaRemanescenteVegetacaoNativa}","areaUsoRestrito": "${areaUsoRestrito}","areaUsoRestritoDeclividade": "${areaUsoRestritoDeclividade}","areaReservaLegalExcedentePassivo": "${areaReservaLegalExcedentePassivo}","areaReservaLegalRecompor": "${areaReservaLegalRecompor}","areaPreservacaoPermanenteRecompor": "${areaPreservacaoPermanenteRecompor}","areaUsoRestritoRecompor": "${areaUsoRestritoRecompor}","sobreposicoesTerraIndigena": "${sobreposicoesTerraIndigena}","sobreposicoesUnidadeConservacao": "${sobreposicoesUnidadeConservacao}","sobreposicoesAreasEmbargadas": "${sobreposicoesAreasEmbargadas}"}}', '2024-10-24 12:32:38.065', NULL);

INSERT INTO certify.credential_template
(context, credential_type, "template", cr_dtimes, upd_dtimes)
VALUES('https://www.w3.org/2018/credentials/v1', 'CARReceiptAST,VerifiableCredential', '{"@context":["https://www.w3.org/2018/credentials/v1"],"issuer":"${_issuer}","type":["VerifiableCredential","CARReceipt"],"issuanceDate":"${validFrom}","expirationDate":"${validUntil}","credentialSubject":{"codigoImovel":"${codigoImovel}","dataCadastro":"${dataCadastro}","nomeImovel":"${nomeImovel}","municipio":"${municipio}","tipoImovel":"${tipoImovel}","unidadeFederativa":"${unidadeFederativa}","coordenadaImovelX":"${coordenadaImovelX}","coordenadaImovelY":"${coordenadaImovelY}","areaTotalImovel":"${areaTotalImovel}","moduloFiscal":"${moduloFiscal}","protocolo":"${protocolo}","informacoesAdicionais":"${informacoesAdicionais}","geoImovel":"${geoImovel}","proprietarios":"${proprietarios}","areaServidaoAdministrativa":"${areaServidaoAdministrativa}","areaUsoRestrito":"${areaUsoRestrito}","areaPreservacaoPermanente":"${areaPreservacaoPermanente}","areaConsolidada":"${areaConsolidada}","areaRemanescenteVegetacaoNativa":"${areaRemanescenteVegetacaoNativa}","areaReservaLegal":"${areaReservaLegal}","areaLiquidaImovel":"${areaLiquidaImovel}","matricula":"${matricula}","dataMatricula":"${dataMatricula}","livroMatricula":"${livroMatricula}","folhaMatricula":"${folhaMatricula}","municipioCartorio":"${municipioCartorio}","ufCartorio":"${ufCartorio}","cpfCnpj":"${cpfCnpj}","nome":"${nome}"}}', '2024-10-24 12:32:38.065', NULL);

INSERT INTO certify.credential_template
(context, credential_type, "template", cr_dtimes, upd_dtimes)
VALUES('https://www.w3.org/2018/credentials/v1', 'CAFCredential,VerifiableCredential', '{"@context": ["https://www.w3.org/2018/credentials/v1"],"issuer": "${_issuer}","type": ["VerifiableCredential","CAFCredential"],"issuanceDate": "${validFrom}","expirationDate": "${validUntil}","credentialSubject": {"enquadramentoPronaf": "${enquadramentoPronaf}","situacao": "${situacao}","dataCriacao": "${dataCriacao}","dataValidade": "${dataValidade}","numeroCaf": "${numeroCaf}","Ultima_Atualizacao": "${Ultima_Atualizacao}","atividadeprincipalUFPA": "${atividadeprincipalUFPA}","caracterizacaoArea": "${caracterizacaoArea}","membros_tipoMembro_descricao": "${membros_tipoMembro_descricao}","possuiMaoObraContratada": "${possuiMaoObraContratada}","membros_nome": "${membros_nome}","membros_cpf": "${membros_cpf}","areas_condicaoPosse": "${areas_condicaoPosse}","areas_tamanho": "${areas_tamanho}","areas_tamanho_areas_unidadeMedida_descricao": "${areas_tamanho_areas_unidadeMedida_descricao}","municipio": "${municipio}","entidadeEmissora_razaoSocial": "${entidadeEmissora_razaoSocial}","entidadeEmissora_cnpj": "${entidadeEmissora_cnpj}","emissor": "${emissor}"}}', '2024-10-24 12:32:38.065', NULL);

INSERT INTO certify.credential_template
(context, credential_type, "template", cr_dtimes, upd_dtimes)
VALUES('https://www.w3.org/2018/credentials/v1', 'CARReceiptPCT,VerifiableCredential', '{"@context":["https://www.w3.org/2018/credentials/v1"],"issuer":"${_issuer}","type":["VerifiableCredential","CARReceipt"],"issuanceDate":"${validFrom}","expirationDate":"${validUntil}","credentialSubject":{"identificadorImovel":"${identificadorImovel}","codigoImovel":"${codigoImovel}","situacaoImovel":"${situacaoImovel}","tipoImovel":"${tipoImovel}","dataCadastro":"${dataCadastro}","nomeImovel":"${nomeImovel}","codigoMunicipio":"${codigoMunicipio}","municipio":"${municipio}","unidadeFederativa":"${unidadeFederativa}","coordenadaImovelX":"${coordenadaImovelX}","coordenadaImovelY":"${coordenadaImovelY}","areaTotalImovel":"${areaTotalImovel}","moduloFiscal":"${moduloFiscal}","protocolo":"${protocolo}","informacoesAdicionais":"${informacoesAdicionais}","geoImovel":"${geoImovel}","areaServidaoAdministrativa":"${areaServidaoAdministrativa}","areaLiquidaImovel":"${areaLiquidaImovel}","areaPreservacaoPermanente":"${areaPreservacaoPermanente}","areaUsoRestrito":"${areaUsoRestrito}","areaConsolidada":"${areaConsolidada}","areaRemanescenteVegetacaoNativa":"${areaRemanescenteVegetacaoNativa}","areaReservaLegal":"${areaReservaLegal}","matricula":"${matricula}","dataMatricula":"${dataMatricula}","livroMatricula":"${livroMatricula}","folhaMatricula":"${folhaMatricula}","municipioCartorio":"${municipioCartorio}","ufCartorio":"${ufCartorio}"}}', '2025-10-14 16:46:45.486', NULL);

INSERT INTO certify.credential_template
(context, credential_type, "template", cr_dtimes, upd_dtimes)
VALUES('https://www.w3.org/2018/credentials/v1', 'CCIR,VerifiableCredential', '{"@context": ["https://www.w3.org/2018/credentials/v1"],"issuer": "${_issuer}","type": ["VerifiableCredential","CCIRCredential"],"issuanceDate": "${validFrom}","expirationDate": "${validUntil}","credentialSubject": {"id": "did:web:shubhm-m.github.io:certify:test#key-0" ,"codigoImovelIncra": "${codigoImovelIncra}","denominacao": "${denominacao}","areaTotal": "${areaTotal}","classificacaoFundiaria": "${classificacaoFundiaria}","dataProcessamentoUltimaDeclaracao": "${dataProcessamentoUltimaDeclaracao}","areaCertificada": "${areaCertificada}","indicacoesLocalizacao": "${indicacoesLocalizacao}","municipioSede": "${municipioSede}","ufSede": "${ufSede}","areaModuloRural": "${areaModuloRural}","numeroModulosRurais": "${numeroModulosRurais}","areaModuloFiscal": "${areaModuloFiscal}","numeroModulosFiscais": "${numeroModulosFiscais}","fracaoMinimaParcelamento": "${fracaoMinimaParcelamento}","totalAreaRegistrada": "${totalAreaRegistrada}","totalAreaPosseJustoTitulo": "${totalAreaPosseJustoTitulo}","totalAreaPosseSimplesOcupacao": "${totalAreaPosseSimplesOcupacao}","areaMedida": "${areaMedida}","declarante": "${declarante}","cpfCnpj": "${cpfCnpj}","nacionalidade": "${nacionalidade}","totalPessoasRelacionadasImovel": "${totalPessoasRelacionadasImovel}","nomeTitular": "${nomeTitular}","condicaoTitularidade": "${condicaoTitularidade}","percentualDetencao": "${percentualDetencao}","dataLancamento": "${dataLancamento}","numeroCcir": "${numeroCcir}","dataGeracaoCcir": "${dataGeracaoCcir}","dataVencimentoCcir": "${dataVencimentoCcir}","debitosAnteriores": "${debitosAnteriores}","taxaServicosCadastrais": "${taxaServicosCadastrais}","valorCobrado": "${valorCobrado}","multa": "${multa}","juros": "${juros}","valorTotal": "${valorTotal}"}}', '2024-10-24 12:32:38.065', NULL);

INSERT INTO certify.credential_template
(context, credential_type, "template", cr_dtimes, upd_dtimes)
VALUES('https://www.w3.org/2018/credentials/v1', 'ECACredential,VerifiableCredential', '{"@context": ["https://www.w3.org/2018/credentials/v1"],"issuer": "${_issuer}","type": ["VerifiableCredential","ECACredential"],"issuanceDate": "${validFrom}","expirationDate": "${validUntil}","credentialSubject": {"id": "${_holderId}","isOver12": ${isOver12},"isOver14": ${isOver14},"isOver16": ${isOver16},"isOver18": ${isOver18}}}', now(), NULL);
