package io.mosip.certify;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.api.spi.DataProviderPlugin;
import io.mosip.certify.dataprovider234.DataProviderService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.Map;

//@ConditionalOnProperty(value = "mosip.certify.integration.vci-plugin", havingValue = "TestVCIPluginImpl")
@Component
@Slf4j
public class TestMockDataProviderPluginImpl implements DataProviderPlugin {
    private final DataProviderService dataProviderService;

    public TestMockDataProviderPluginImpl(DataProviderService dataProviderService) {
        this.dataProviderService = dataProviderService;
    }

    @Override
    public JSONObject fetchData(Map<String, Object> identityDetails) throws DataProviderExchangeException {
        //        return Map.of();
//        JSONObject obj = new JSONObject();
        JSONObject data = null;
        try {
            data = dataProviderService.getData();
            return data;
//            obj.put("codigoImovelIncra","950.092.175.501-0");
//            obj.put("denominacao","Fazenda da Galinha");
//            obj.put("areaTotal","703,0694");
//            obj.put("classificacaoFundiaria","Grande Propriedade Produtiva");
//            obj.put("dataProcessamentoUltimaDeclaracao","02/05/2022");
//            obj.put("areaCertificada","0,0000");
//            obj.put("indicacoesLocalizacao","Passinhos");
//            obj.put("municipioSede","CAPIVARI DO SUL");
//            obj.put("ufSede","RS");
//            obj.put("areaModuloRural","23,2142");
//            obj.put("numeroModulosRurais","28,00");
//            obj.put("numeroModulosFiscais","18,0000");
//            obj.put("fracaoMinimaParcelamento","39,0594");
//            obj.put("totalAreaRegistrada","0,0000");
//            obj.put("totalAreaPosseJustoTitulo","703,0694");
//            obj.put("totalAreaPosseSimplesOcupacao","0,0000");
//            obj.put("areaMedida"," ");
//            obj.put("declarante","TOMAZ");
//            obj.put("cpfCnpj","455.6");
//            obj.put("totalPessoasRelacionadasImovel","2");
//            obj.put("nomeTitular","TOMAZ");
//            obj.put("condicaoTitularidade","Proprietario Ou Posseiro Comum");
//            obj.put("percentualDetencao","70,10\\/n29,90");
//            obj.put("dataLancamento","18/07/2022");
//            obj.put("numeroCcir","2454353323");
//            obj.put("dataGeracaoCcir","1/08/2022");
//            obj.put("dataVencimentoCcir","17/08/2022");
//            obj.put("debitosAnteriores","0,00");
//            obj.put("taxaServicosCadastrais","72,93");
//            obj.put("valorCobrado","72,93");
//            obj.put("multa","0,00");
//            obj.put("juros","0,00");
//            obj.put("valorTotal","72,93");
        }catch (Exception ex){
            log.info("Exception while fetching data from TestMockDataProviderPluginImpl", ex);
            return null;
        }
    }
}
