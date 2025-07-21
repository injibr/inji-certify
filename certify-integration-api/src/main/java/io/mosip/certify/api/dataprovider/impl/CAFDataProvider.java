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
    public JSONObject getData() throws JSONException {
        return new JSONObject()
                .put("enquadramentoPronaf", "Simples Nacional")
                .put("situacao", "Ativo")
                .put("dataCriacao", "2023-01-01")
                .put("dataValidade", "2026-01-01")
                .put("numeroCaf", "CAF123456789")
                .put("Ultima Atualização:", "2025-07-15")
                .put("atividadeprincipalUFPA", "Agricultura")
                .put("caracterizacaoArea", "Área Rural")
                .put("membros -> tipoMembro -> descricao", "Titular")
                .put("possuiMaoObraContratada", true)
                .put("membros -> nome", "João Silva")
                .put("membros -> cpf", "123.456.789-00")
                .put("areas -> condicaoPosse", "Proprietário")
                .put("areas -> tamanho", 10.5)
                .put("areas -> tamanho + areas -> (unidadeMedida -> descricao)", "10.5 hectares")
                .put("municipio", "São Paulo")
                .put("entidadeEmissora -> razaoSocial", "Instituto Nacional de Agricultura")
                .put("entidadeEmissora -> cnpj", "12.345.678/0001-99")
                .put("emissor", "Sistema CAF");
    }
}
