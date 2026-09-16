package br.com.gontijo.pto.comum;

public class Config {
    
    private String relogio;
    private String baseConexao;
    private String portaSerial;
    private String modeloRelogio;
    
    public Config() {
        super();
    }

    public Config(String relogio, String baseConexao, String portaSerial, String modeloRelogio) {
        this.relogio = relogio;
        this.baseConexao = baseConexao;
        this.portaSerial = portaSerial;
        this.modeloRelogio = modeloRelogio;
    }

    public void setRelogio(String relogio) {
        this.relogio = relogio;
    }

    public String getRelogio() {
        return relogio;
    }

    public void setBaseConexao(String baseConexao) {
        this.baseConexao = baseConexao;
    }

    public String getBaseConexao() {
        return baseConexao;
    }

    public void setPortaSerial(String portaSerial) {
        this.portaSerial = portaSerial;
    }
   
    public String getPortaSerial() {
        return portaSerial;
    }
    
    public void setModeloRelogio(String modeloRelogio) {
        this.modeloRelogio = modeloRelogio;
    }    
   
    public String getModeloRelogio() {
        return modeloRelogio;
    }

}
