package io.mosip.certify.api.dataprovider.impl;

import io.mosip.certify.api.dataprovider.DataProviderService;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
public class CAFDataProvider implements DataProviderService {

    @Override
    public String getDocumentType() {
        return "CAFCredential";
    }

    @Override
    public JSONObject getData(String cpfNumber) throws JSONException {
        return new JSONObject()
                .put("enquadramentoPronaf", "Simples Nacional")
                .put("situacao", "Ativo")
                .put("dataCriacao", "2023-01-01")
                .put("dataValidade", "2026-01-01")
                .put("numeroCaf", "CAF123456789")
                .put("Ultima_Atualizacao", "2025-07-15")
                .put("atividadeprincipalUFPA", "Agricultura")
                .put("caracterizacaoArea", "Área Rural")
                .put("membros_tipoMembro_descricao", "Titular")
                .put("possuiMaoObraContratada", "true")
                .put("membros_nome", "João Silva")
                .put("membros_cpf", "123.456.789-00")
                .put("areas_condicaoPosse", "Proprietário")
                .put("areas_tamanho", 10.5)
                .put("areas_tamanho_areas_unidadeMedida_descricao", "10.5 hectares")
                .put("municipio", "São Paulo")
                .put("entidadeEmissora_razaoSocial", "Instituto Nacional de Agricultura")
                .put("entidadeEmissora_cnpj", "12.345.678/0001-99")
                .put("emissor", "Sistema CAF");
    }
}
