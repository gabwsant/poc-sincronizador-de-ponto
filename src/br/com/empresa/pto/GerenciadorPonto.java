package br.com.empresa.pto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.empresa.pto.comum.Config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class GerenciadorPonto {
    private static Config config;
    private static Logger logger = LoggerFactory.getLogger(GerenciadorPonto.class);

    private final ReentrantLock lock;
    
    public GerenciadorPonto(ReentrantLock lock) {
        super();
        this.lock = lock;
    }
    
    private void config() throws Exception {
        logger.debug("Iniciando configuracao.");

        config = new Config();
        config.setRelogio("PTRL00050");
        config.setBaseConexao("D");
        config.setPortaSerial("COM4");
        config.setModeloRelogio("CIS");
        
        logger.info("Configuracao concluida.");
    }
    
    public static void main(String[] args) throws Exception {
        ReentrantLock lockDoArquivo = new ReentrantLock();

        GerenciadorPonto ponto = new GerenciadorPonto(lockDoArquivo);

        // Sincronizador de pontos
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(new Sincronizador(config, lockDoArquivo), 1, 60, TimeUnit.SECONDS);
        
        //...
    }
}