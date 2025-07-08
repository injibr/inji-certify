CREATE DATABASE inji_certify
  ENCODING = 'UTF8'
  LC_COLLATE = 'en_US.UTF-8'
  LC_CTYPE = 'en_US.UTF-8'
  TABLESPACE = pg_default
  OWNER = postgres
  TEMPLATE  = template0;

COMMENT ON DATABASE inji_certify IS 'certify related data is stored in this database';

\c inji_certify postgres

DROP SCHEMA IF EXISTS certify CASCADE;
CREATE SCHEMA certify;
ALTER SCHEMA certify OWNER TO postgres;
ALTER DATABASE inji_certify SET search_path TO certify,pg_catalog,public;

--- keymanager specific DB changes ---
CREATE TABLE certify.key_alias(
                                  id character varying(36) NOT NULL,
                                  app_id character varying(36) NOT NULL,
                                  ref_id character varying(128),
                                  key_gen_dtimes timestamp,
                                  key_expire_dtimes timestamp,
                                  status_code character varying(36),
                                  lang_code character varying(3),
                                  cr_by character varying(256) NOT NULL,
                                  cr_dtimes timestamp NOT NULL,
                                  upd_by character varying(256),
                                  upd_dtimes timestamp,
                                  is_deleted boolean DEFAULT FALSE,
                                  del_dtimes timestamp,
                                  cert_thumbprint character varying(100),
                                  uni_ident character varying(50),
                                  CONSTRAINT pk_keymals_id PRIMARY KEY (id),
                                  CONSTRAINT uni_ident_const UNIQUE (uni_ident)
);

CREATE TABLE certify.key_policy_def(
                                       app_id character varying(36) NOT NULL,
                                       key_validity_duration smallint,
                                       is_active boolean NOT NULL,
                                       pre_expire_days smallint,
                                       access_allowed character varying(1024),
                                       cr_by character varying(256) NOT NULL,
                                       cr_dtimes timestamp NOT NULL,
                                       upd_by character varying(256),
                                       upd_dtimes timestamp,
                                       is_deleted boolean DEFAULT FALSE,
                                       del_dtimes timestamp,
                                       CONSTRAINT pk_keypdef_id PRIMARY KEY (app_id)
);

CREATE TABLE certify.key_store(
                                  id character varying(36) NOT NULL,
                                  master_key character varying(36) NOT NULL,
                                  private_key character varying(2500) NOT NULL,
                                  certificate_data character varying NOT NULL,
                                  cr_by character varying(256) NOT NULL,
                                  cr_dtimes timestamp NOT NULL,
                                  upd_by character varying(256),
                                  upd_dtimes timestamp,
                                  is_deleted boolean DEFAULT FALSE,
                                  del_dtimes timestamp,
                                  CONSTRAINT pk_keystr_id PRIMARY KEY (id)
);

CREATE TABLE certify.ca_cert_store(
    cert_id character varying(36) NOT NULL,
    cert_subject character varying(500) NOT NULL,
    cert_issuer character varying(500) NOT NULL,
    issuer_id character varying(36) NOT NULL,
    cert_not_before timestamp,
    cert_not_after timestamp,
    crl_uri character varying(120),
    cert_data character varying,
    cert_thumbprint character varying(100),
    cert_serial_no character varying(50),
    partner_domain character varying(36),
    cr_by character varying(256),
    cr_dtimes timestamp,
    upd_by character varying(256),
    upd_dtimes timestamp,
    is_deleted boolean DEFAULT FALSE,
    del_dtimes timestamp,
    ca_cert_type character varying(25),
    CONSTRAINT pk_cacs_id PRIMARY KEY (cert_id),
    CONSTRAINT cert_thumbprint_unique UNIQUE (cert_thumbprint,partner_domain)

);

CREATE TABLE certify.rendering_template (
                                    id varchar(128) NOT NULL,
                                    template VARCHAR NOT NULL,
                                    cr_dtimes timestamp NOT NULL,
                                    upd_dtimes timestamp,
                                    CONSTRAINT pk_svgtmp_id PRIMARY KEY (id)
);

CREATE TABLE certify.credential_template(
                                    context character varying(1024) NOT NULL,
                                    credential_type character varying(512) NOT NULL,
                                    template VARCHAR NOT NULL,
                                    cr_dtimes timestamp NOT NULL default now(),
                                    upd_dtimes timestamp,
                                    CONSTRAINT pk_template PRIMARY KEY (context, credential_type)
);

CREATE TABLE certify.certify_keys (
	config_key varchar NULL,
	config_value text NULL
);

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

INSERT INTO certify.credential_template (context, credential_type, template, cr_dtimes, upd_dtimes) VALUES ('https://www.w3.org/2018/credentials/v1', 'CCIR,VerifiableCredential', '{"@context": ["https://www.w3.org/2018/credentials/v1"],"issuer": "${_issuer}","type": ["VerifiableCredential","FarmerCredential"],"issuanceDate": "${validFrom}","expirationDate": "${validUntil}","credentialSubject": {"id": "did:web:shubhm-m.github.io:certify:test#key-0" ,"codigoImovelIncra": "${codigoImovelIncra}","denominacao": "${denominacao}","areaTotal": "${areaTotal}","classificacaoFundiaria": "${classificacaoFundiaria}","dataProcessamentoUltimaDeclaracao": "${dataProcessamentoUltimaDeclaracao}","areaCertificada": "${areaCertificada}","indicacoesLocalizacao": "${indicacoesLocalizacao}","municipioSede": "${municipioSede}","ufSede": "${ufSede}","areaModuloRural": "${areaModuloRural}","numeroModulosRurais": "${numeroModulosRurais}","areaModuloFiscal": "${areaModuloFiscal}","numeroModulosFiscais": "${numeroModulosFiscais}","fracaoMinimaParcelamento": "${fracaoMinimaParcelamento}","totalAreaRegistrada": "${totalAreaRegistrada}","totalAreaPosseJustoTitulo": "${totalAreaPosseJustoTitulo}","totalAreaPosseSimplesOcupacao": "${totalAreaPosseSimplesOcupacao}","areaMedida": "${areaMedida}","declarante": "${declarante}","cpfCnpj": "${cpfCnpj}","nacionalidade": "${nacionalidade}","totalPessoasRelacionadasImovel": "${totalPessoasRelacionadasImovel}","nomeTitular": "${nomeTitular}","condicaoTitularidade": "${condicaoTitularidade}","percentualDetencao": "${percentualDetencao}","dataLancamento": "${dataLancamento}","numeroCcir": "${numeroCcir}","dataGeracaoCcir": "${dataGeracaoCcir}","dataVencimentoCcir": "${dataVencimentoCcir}","debitosAnteriores": "${debitosAnteriores}","taxaServicosCadastrais": "${taxaServicosCadastrais}","valorCobrado": "${valorCobrado}","multa": "${multa}","juros": "${juros}","valorTotal": "${valorTotal}"}}', '2024-10-24 12:32:38.065994', NULL);
INSERT INTO certify.credential_template (context, credential_type, template, cr_dtimes, upd_dtimes) VALUES ('https://www.w3.org/2018/credentials/v1', 'CARReceiptDocument,VerifiableCredential', '{"@context": ["https://www.w3.org/2018/credentials/v1"],"issuer": "${_issuer}","type": ["VerifiableCredential","FarmerCredential"],"issuanceDate": "${validFrom}","expirationDate": "${validUntil}","credentialSubject": {"codigoImovel": "${codigoImovel}","dataCadastro": "${dataCadastro}","nomeImovel": "${nomeImovel}","municipio": "${municipio}","unidadefederativa": "${unidadefederativa}","coordenadaImovelX": "${coordenadaImovelX}","coordenadaImovelY": "${coordenadaImovelY}","areaTotalImovel": "${areaTotalImovel}","moduloFiscal": "${moduloFiscal}","protocolo": "${protocolo}","areaLiquidaImovel": "${areaLiquidaImovel}","areaPreservacaoPermanente": "${areaPreservacaoPermanente}","areaReservaLegal": "${areaReservaLegal}","areaUsoRestrito": "${areaUsoRestrito}","areaConsolidada": "${areaConsolidada}","areaRemanescenteVegetacaoNativa": "${areaRemanescenteVegetacaoNativa}","cpfCnpj": "${cpfCnpj}","informacoesAdicionais": "${informacoesAdicionais}","geoImovel": "${geoImovel}","areaServidaoAdministrativa": "${areaServidaoAdministrativa}","nomeProprietario": "${nomeProprietario}"}}', '2024-10-24 12:32:38.065994', NULL);
INSERT INTO certify.credential_template (context, credential_type, template, cr_dtimes, upd_dtimes) VALUES ('https://www.w3.org/2018/credentials/v1', 'CAR,VerifiableCredential', '{"@context": ["https://www.w3.org/2018/credentials/v1"],"issuer": "${_issuer}","type": ["VerifiableCredential","FarmerCredential"],"issuanceDate": "${validFrom}","expirationDate": "${validUntil}","credentialSubject": {"situacaoImovel": "${situacaoImovel}","codigoImovel": "${codigoImovel}","descricaoEtapaCadastro": "${descricaoEtapaCadastro}","areaTotalImovel": "${areaTotalImovel}","quantidadeModulosFiscais": "${quantidadeModulosFiscais}","dataCadastro": "${dataCadastro}","dataUltimaAtualizacaoCadastro": "${dataUltimaAtualizacaoCadastro}","municipio": "${municipio}","unidadeFederativa": "${unidadeFederativa}","coordenadasImovelX": "${coordenadasImovelX}","coordenadasIimovelY": "${coordenadasIimovelY}","areaRemanescenteVegetacaoNativa": "${areaRemanescenteVegetacaoNativa}","areaConsolidada": "${areaConsolidada}","areaServidaoAdministrativa": "${areaServidaoAdministrativa}","situacaoReservaLegal": "${situacaoReservaLegal}","areaReservaLegalAverbadaDocumental": "${areaReservaLegalAverbadaDocumental}","areaReservaLegalAverbada": "${areaReservaLegalAverbada}","areaReservaLegalAprovadaNaoAverbada": "${areaReservaLegalAprovadaNaoAverbada}","areaReservaLegalProposta": "${areaReservaLegalProposta}","areaReservaLegalDeclaradaProprietarioPossuidor": "${areaReservaLegalDeclaradaProprietarioPossuidor}","areaPreservacaoPermanente": "${areaPreservacaoPermanente}","areaPreservacaoPermanenteAreaRuralConsolidada": "${areaPreservacaoPermanenteAreaRuralConsolidada}","areaPreservacaoPermanenteAreaRemanescenteVegetacaoNativa": "${areaPreservacaoPermanenteAreaRemanescenteVegetacaoNativa}","areaUsoRestrito": "${areaUsoRestrito}","areaUsoRestritoDeclividade": "${areaUsoRestritoDeclividade}","areaReservaLegalPassivoExcedente": "${areaReservaLegalPassivoExcedente}","areaReservaLegalRecompor": "${areaReservaLegalRecompor}","areaPreservacaoPermanenteRecompor": "${areaPreservacaoPermanenteRecompor}","areaUsoRestritoRecompor": "${areaUsoRestritoRecompor}","sobreposicoesTerraIndigena": "${sobreposicoesTerraIndigena}","sobreposicoesUnidadeConservacao": "${sobreposicoesUnidadeConservacao}","sobreposicoesAreasEmbargadas": "${sobreposicoesAreasEmbargadas}"}}', '2024-10-24 12:32:38.065994', NULL);

INSERT INTO certify.certify_keys
(config_key, config_value)
VALUES('CCIR_mock', '{"CCIR_mock": {"credential_issuer": "${mosip.certify.identifier}","authorization_servers": ["${mosip.certify.authorization.url}"],"credential_endpoint": "${mosipbox.public.url}${server.servlet.path}/issuance/credential","display": [{"name": "CERTIFICADO DE CADASTRO DE IMÃVEL RURAL","locale": "pt"}],"credential_configurations_supported": {"CCIRCredential": {"format": "ldp_vc","scope": "CCIR","cryptographic_binding_methods_supported": ["did:jwk"],"credential_signing_alg_values_supported": ["Ed25519Signature2020"],"proof_types_supported": {"jwt": {"proof_signing_alg_values_supported": ["RS256","PS256"]}},"credential_definition": {"type": ["VerifiableCredential","CCIR"],"credentialSubject": {"codigoImovelIncra": {"display": [{"name": "CÓDIGO DO IMÓVEL RURAL","locale": "en"}]},"denominacao": {"display": [{"name": "DENOMINAÇÃO DO IMÓVEL RURAL","locale": "en"}]},"areaTotal": {"display": [{"name": "ÁREA TOTAL (ha)","locale": "en"}]},"classificacaoFundiaria": {"display": [{"name": "CLASSIFICAÇÃO FUNDIÁRIA","locale": "en"}]},"dataProcessamentoUltimaDeclaracao": {"display": [{"name": "DATA DO PROCESSAMENTO DA ÚLTIMA DECLARAÇÃO","locale": "en"}]},"areaCertificada": {"display": [{"name": "ÁREA CERTIFICADA7","locale": "en"}]},"indicacoesLocalizacao": {"display": [{"name": "INDICAÇÕES PARA LOCALIZAÇÃO DO IMÓVEL RURAL","locale": "en"}]},"municipioSede": {"display": [{"name": "MUNICÍPIO SEDE DO IMÓVEL RURAL","locale": "en"}]},"ufSede": {"display": [{"name": "UF","locale": "en"}]},"areaModuloRural": {"display": [{"name": "MÓDULO RURAL (ha)","locale": "en"}]},"numeroModulosRurais": {"display": [{"name": "Nº MÓDULOS RURAIS","locale": "en"}]},"areaModuloFiscal": {"display": [{"name": "MÓDULO FISCAL (ha)","locale": "en"}]},"numeroModulosFiscais": {"display": [{"name": "Nº MÓDULOS FISCAIS","locale": "en"}]},"fracaoMinimaParcelamento": {"display": [{"name": "FRAÇÃO MÍNIMA DE PARCELAMENTO (ha)","locale": "en"}]},"totalAreaRegistrada": {"display": [{"name": "REGISTRADA","locale": "en"}]},"totalAreaPosseJustoTitulo": {"display": [{"name": "POSSE A JUSTO TÍTULO","locale": "en"}]},"totalAreaPosseSimplesOcupacao": {"display": [{"name": "POSSE POR SIMPLES OCUPAÇÃO","locale": "en"}]},"areaMedida": {"display": [{"name": "ÁREA MEDIDA","locale": "en"}]},"declarante": {"display": [{"name": "NOME","locale": "en"}]},"cpfCnpj": {"display": [{"name": "CPF/CNPJ","locale": "en"}]},"nacionalidade": {"display": [{"name": "NACIONALIDADE","locale": "en"}]},"totalPessoasRelacionadasImovel": {"display": [{"name": "TOTAL DE PESSOAS RELACIONADAS AO IMÓVEL","locale": "en"}]},"nomeTitular": {"display": [{"name": "NOME1","locale": "en"}]},"condicaoTitularidade": {"display": [{"name": "CONDIÇÃO","locale": "en"}]},"percentualDetencao": {"display": [{"name": "DETENÇÃO (%)","locale": "en"}]},"dataLancamento": {"display": [{"name": "DATA DE LANÇAMENTO","locale": "en"}]},"numeroCcir": {"display": [{"name": "NÚMERO DO CCIR","locale": "en"}]},"dataGeracaoCcir": {"display": [{"name": "DATA DE GERAÇÃO DO CCIR","locale": "en"}]},"dataVencimentoCcir": {"display": [{"name": "DATA DE VENCIMENTO","locale": "en"}]},"debitosAnteriores": {"display": [{"name": "DÉBITOS ANTERIORES","locale": "en"}]},"taxaServicosCadastrais": {"display": [{"name": "TAXA DE SERVIÇOS CADASTRAIS","locale": "en"}]},"valorCobrado": {"display": [{"name": "VALOR COBRADO","locale": "en"}]},"multa": {"display": [{"name": "MULTA","locale": "en"}]},"juros": {"display": [{"name": "JUROS","locale": "en"}]},"valorTotal": {"display": [{"name": "VALOR TOTAL","locale": "en"}]}}},"display": [{"name": "CERTIFICADO DE CADASTRO DE IMÓVEL RURAL","locale": "pt","logo": {"url": "https://raw.githubusercontent.com/kunalash/Files/refs/heads/main/logo.png","alt_text": "a square logo of CCIR"},"background_color": "#FDFAF9","background_image": {"uri": "https://sunbird.org/images/sunbird-logo-new.png"},"text_color": "#7C4616"}],"order": ["codigoImovelIncra","denominacao","areaTotal","classificacaoFundiaria","dataProcessamentoUltimaDeclaracao","areaCertificada","indicacoesLocalizacao","municipioSede","ufSede","areaModuloRural","numeroModulosRurais","areaModuloFiscal","numeroModulosFiscais","fracaoMinimaParcelamento","totalAreaRegistrada","totalAreaPosseJustoTitulo","totalAreaPosseSimplesOcupacao","areaMedida","declarante","cpfCnpj","nacionalidade","totalPessoasRelacionadasImovel","nomeTitular","condicaoTitularidade","percentualDetencao","dataLancamento","numeroCcir","dataGeracaoCcir","dataVencimentoCcir","debitosAnteriores","taxaServicosCadastrais","valorCobrado","multa","juros","valorTotal"]}}}}');
INSERT INTO certify.certify_keys
(config_key, config_value)
VALUES('CAR_mock', '{"CAR_mock": {"credential_issuer": "${mosip.certify.identifier}","authorization_servers": ["${mosip.certify.authorization.url}"],"credential_endpoint": "${mosipbox.public.url}${server.servlet.path}/issuance/credential","display": [{"name": "CAR","locale": "en"}],"credential_configurations_supported": {"CARReceipt": {"format": "ldp_vc","scope": "CARReceiptDocument","cryptographic_binding_methods_supported": ["did:jwk"],"credential_signing_alg_values_supported": ["Ed25519Signature2020"],"proof_types_supported": {"jwt": {"proof_signing_alg_values_supported": ["RS256","PS256"]}},"credential_definition": {"type": ["VerifiableCredential","CARReceiptDocument"],"credentialSubject": {"codigoImovel": {"display": [{"name": "Código do Imóvel","locale": "pt"}]},"dataCadastro": {"display": [{"name": "Data de Cadastro","locale": "pt"}]},"nomeImovel": {"display": [{"name": "Nome do Imóvel","locale": "pt"}]},"municipio": {"display": [{"name": "Município","locale": "pt"}]},"unidadefederativa": {"display": [{"name": "Estado","locale": "pt"}]},"coordenadaImovelX": {"display": [{"name": "Longitude","locale": "pt"}]},"coordenadaImovelY": {"display": [{"name": "Latitude","locale": "pt"}]},"areaTotalImovel": {"display": [{"name": "Área Total do Imóvel","locale": "pt"}]},"moduloFiscal": {"display": [{"name": "Módulo Fiscal","locale": "pt"}]},"protocolo": {"display": [{"name": "Protocolo","locale": "pt"}]},"areaLiquidaImovel": {"display": [{"name": "Área Líquida do Imóvel","locale": "pt"}]},"areaPreservacaoPermanente": {"display": [{"name": "Área de Preservação Permanente","locale": "pt"}]},"areaReservaLegal": {"display": [{"name": "Área de Reserva Legal","locale": "pt"}]},"areaUsoRestrito": {"display": [{"name": "Área de Uso Restrito","locale": "pt"}]},"areaConsolidada": {"display": [{"name": "Área Consolidada","locale": "pt"}]},"areaRemanescenteVegetacaoNativa": {"display": [{"name": "Área de Vegetação Nativa","locale": "pt"}]},"cpfCnpj": {"display": [{"name": "CPF/CNPJ do Proprietário","locale": "pt"}]},"informacoesAdicionais": {"display": [{"name": "Informações adicionais sobre o imóvel","locale": "pt"}]},"geoImovel": {"display": [{"name": "Coordenadas do polígono da área do imóvel","locale": "pt"}]},"areaServidaoAdministrativa": {"display": [{"name": "Área de servidão administrativa do imóvel (em hectares)","locale": "pt"}]},"nomeProprietario": {"display": [{"name": "Nome do Proprietário","locale": "pt"}]}}},"display": [{"name": "RECIBO DE INSCRIÇÃO DO IMÓVEL RURAL NO CAR","locale": "pt","logo": {"url": "https://raw.githubusercontent.com/kunalash/Files/refs/heads/main/logo.png","alt_text": "a square logo of CCIR"},"background_color": "#FDFAF9","background_image": {"uri": "https://sunbird.org/images/sunbird-logo-new.png"},"text_color": "#7C4616"},{"name": "RECIBO DE INSCRIÇÃO DO IMÓVEL RURAL NO CAR","locale": "en","logo": {"url": "https://raw.githubusercontent.com/kunalash/Files/refs/heads/main/logo.png","alt_text": "a square logo of CCIR"},"background_color": "#FDFAF9","background_image": {"uri": "https://sunbird.org/images/sunbird-logo-new.png"},"text_color": "#7C4616"}],"order": ["codigoImovel","dataCadastro","nomeImovel","municipio","unidadefederativa","coordenadaImovelX","coordenadaImovelY","areaTotalImovel","moduloFiscal","informacoesAdicionais","geoImovel","protocolo","areaServidaoAdministrativa","areaLiquidaImovel","areaPreservacaoPermanente","areaReservaLegal","areaUsoRestrito","areaConsolidada","areaRemanescenteVegetacaoNativa","cpfCnpj","nomeProprietario"]},"CARCredentail": {"format": "ldp_vc","scope": "CAR","cryptographic_binding_methods_supported": ["did:jwk"],"credential_signing_alg_values_supported": ["Ed25519Signature2020"],"proof_types_supported": {"jwt": {"proof_signing_alg_values_supported": ["RS256","ES256"]}},"credential_definition": {"type": ["VerifiableCredential","CAR"],"credentialSubject": {"situacaoImovel": {"display": [{"name": "Situação do Imóvel","locale": "pt"}]},"codigoImovel": {"display": [{"name": "Código do Imóvel","locale": "pt"}]},"descricaoEtapaCadastro": {"display": [{"name": "Etapa do Cadastro","locale": "pt"}]},"areaTotalImovel": {"display": [{"name": "Área Total do Imóvel","locale": "pt"}]},"quantidadeModulosFiscais": {"display": [{"name": "Quantidade de Módulos Fiscais","locale": "pt"}]},"dataCadastro": {"display": [{"name": "Data de Cadastro","locale": "pt"}]},"dataUltimaAtualizacaoCadastro": {"display": [{"name": "Data da Última Atualização","locale": "pt"}]},"municipio": {"display": [{"name": "Município","locale": "pt"}]},"unidadeFederativa": {"display": [{"name": "Estado","locale": "pt"}]},"coordenadasImovelX": {"display": [{"name": "Longitude","locale": "pt"}]},"coordenadasIimovelY": {"display": [{"name": "Latitude","locale": "pt"}]},"areaRemanescenteVegetacaoNativa": {"display": [{"name": "Área de Vegetação Nativa","locale": "pt"}]},"areaConsolidada": {"display": [{"name": "Área Consolidada","locale": "pt"}]},"areaServidaoAdministrativa": {"display": [{"name": "Área de Servidão Administrativa","locale": "pt"}]},"situacaoReservaLegal": {"display": [{"name": "Situação da Reserva Legal","locale": "pt"}]},"areaReservaLegalAverbadaDocumental": {"display": [{"name": "Reserva Legal Averbada Documental","locale": "pt"}]},"areaReservaLegalAverbada": {"display": [{"name": "Reserva Legal Averbada","locale": "pt"}]},"areaReservaLegalAprovadaNaoAverbada": {"display": [{"name": "Reserva Legal Aprovada Não Averbada","locale": "pt"}]},"areaReservaLegalProposta": {"display": [{"name": "Reserva Legal Proposta","locale": "pt"}]},"areaReservaLegalDeclaradaProprietarioPossuidor": {"display": [{"name": "Reserva Legal Declarada pelo Proprietário","locale": "pt"}]},"areaPreservacaoPermanente": {"display": [{"name": "Área de Preservação Permanente","locale": "pt"}]},"areaPreservacaoPermanenteAreaRuralConsolidada": {"display": [{"name": "APP - Área Rural Consolidada","locale": "pt"}]},"areaPreservacaoPermanenteAreaRemanescenteVegetacaoNativa": {"display": [{"name": "APP - Vegetação Nativa Remanescente","locale": "pt"}]},"areaUsoRestrito": {"display": [{"name": "Área de Uso Restrito","locale": "pt"}]},"areaUsoRestritoDeclividade": {"display": [{"name": "Uso Restrito por Declividade","locale": "pt"}]},"areaReservaLegalPassivoExcedente": {"display": [{"name": "Passivo ou Excedente da Reserva Legal","locale": "pt"}]},"areaReservaLegalRecompor": {"display": [{"name": "Reserva Legal a Recompor","locale": "pt"}]},"areaPreservacaoPermanenteRecompor": {"display": [{"name": "APP a Recompor","locale": "pt"}]},"areaUsoRestritoRecompor": {"display": [{"name": "Uso Restrito a Recompor","locale": "pt"}]},"sobreposicoesTerraIndigena": {"display": [{"name": "Sobreposição com Terra Indígena","locale": "pt"}]},"sobreposicoesUnidadeConservacao": {"display": [{"name": "Sobreposição com Unidade de Conservação","locale": "pt"}]},"sobreposicoesAreasEmbargadas": {"display": [{"name": "Sobreposição com Áreas Embargadas","locale": "pt"}]}}},"display": [{"name": "CAR - Cadastro Ambiental Rural","locale": "pt","logo": {"url": "https://img.freepik.com/premium-vector/vector-brazil-flag-waving-realistic-flowing-flags_378399-102.jpg","alt_text": "Bandeira do Brasil"},"background_color": "#FDFAF9","background_image": {"uri": "https://img.freepik.com/premium-vector/vector-brazil-flag-waving-realistic-flowing-flags_378399-102.jpg"},"text_color": "#004d00"},{"name": "CAR - Cadastro Ambiental Rural","locale": "en","logo": {"url": "https://img.freepik.com/premium-vector/vector-brazil-flag-waving-realistic-flowing-flags_378399-102.jpg","alt_text": "Bandeira do Brasil"},"background_color": "#FDFAF9","background_image": {"uri": "https://img.freepik.com/premium-vector/vector-brazil-flag-waving-realistic-flowing-flags_378399-102.jpg"},"text_color": "#004d00"}],"order": ["situacaoImovel","codigoImovel","descricaoEtapaCadastro","areaTotalImovel","quantidadeModulosFiscais","dataCadastro","dataUltimaAtualizacaoCadastro","municipio","unidadeFederativa","coordenadasImovelX","coordenadasIimovelY","areaRemanescenteVegetacaoNativa","areaConsolidada","areaServidaoAdministrativa","situacaoReservaLegal","areaReservaLegalAverbadaDocumental","areaReservaLegalAverbada","areaReservaLegalAprovadaNaoAverbada","areaReservaLegalProposta","areaReservaLegalDeclaradaProprietarioPossuidor","areaPreservacaoPermanente","areaPreservacaoPermanenteAreaRuralConsolidada","areaPreservacaoPermanenteAreaRemanescenteVegetacaoNativa","areaUsoRestrito","areaUsoRestritoDeclividade","areaReservaLegalPassivoExcedente","areaReservaLegalRecompor","areaPreservacaoPermanenteRecompor","areaUsoRestritoRecompor","sobreposicoesTerraIndigena","sobreposicoesUnidadeConservacao","sobreposicoesAreasEmbargadas"]}}}}');

INSERT INTO certify.key_policy_def(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES('ROOT', 2920, 1125, 'NA', true, 'mosipadmin', now());
INSERT INTO certify.key_policy_def(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES('CERTIFY_SERVICE', 1095, 60, 'NA', true, 'mosipadmin', now());
INSERT INTO certify.key_policy_def(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES('CERTIFY_PARTNER', 1095, 60, 'NA', true, 'mosipadmin', now());
INSERT INTO certify.key_policy_def(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES('CERTIFY_VC_SIGN_RSA', 1095, 60, 'NA', true, 'mosipadmin', now());
INSERT INTO certify.key_policy_def(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES('CERTIFY_VC_SIGN_ED25519', 1095, 60, 'NA', true, 'mosipadmin', now());
INSERT INTO certify.key_policy_def(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES('BASE', 1095, 60, 'NA', true, 'mosipadmin', now());
