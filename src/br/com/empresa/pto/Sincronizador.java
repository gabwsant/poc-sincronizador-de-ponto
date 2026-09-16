package br.com.empresa.pto;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.sql.Statement;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.empresa.pto.comum.Config;

public class Sincronizador implements Runnable {

    private String url;
    private final String usuario = "Valor omitido";
    private final String senha = "Valor omitido";
    private final Config config;
    private final Path diretorio = Paths.get("C:/Usr6i/pontoe/Relogio");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final Logger logger = LoggerFactory.getLogger(Sincronizador.class);
    private final ReentrantLock lock;

    public Sincronizador(Config config, ReentrantLock lock) {
        super();
        this.config = config;
        this.lock = lock;
    }

    /**
     * Inicialização da thread de sincronização.
     */
    @Override
    public void run() {
        logger.info("Iniciando ciclo de sincronizacao...");
        try {
            if ("D".equals(config.getBaseConexao())) {
                logger.info("Base de dados configurada: Desenvolvimeto (D).");
                url = "Valor omitido";
            } else if ("P".equals(config.getBaseConexao())) {
                logger.info("Base de dados configurada: Producao (P).");
                url = "Valor omitido";
            } else {
                logger.error("Configuracao de base de dados invalida: '{}'", config.getBaseConexao());
                return;
            }

            processarArquivos();

            logger.info("Ciclo de sincronizacao finalizado com sucesso.");
        } catch (Throwable e) {
            logger.error("Erro fatal e inesperado durante a execucao da sincronizacao.", e);
        }
    }

    /**
     * Varre o diretório do ponto em busca de arquivos para sincronização com a base.
     * São válidos para sincronização arquivos gerados até 5 (cinco) dias antes do momento da sincronização.
     */
    private void processarArquivos() {
        LocalDate hoje = LocalDate.now();
        logger.info("Iniciando varredura no diretorio: {}", diretorio.toAbsolutePath());

        try (Connection conexao = getConexao()) {
            logger.info("Conexao com o banco de dados estabelecida com sucesso.");
            conexao.setAutoCommit(false);

            int totalArquivosEncontrados = 0;
            int totalArquivosProcessados = 0;

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(diretorio, "*.pto")) {
                for (Path arquivo : stream) {
                    totalArquivosEncontrados++;
                    LocalDate dataArquivo = extrairDataDoNome(arquivo.getFileName().toString());

                    if (dataArquivo != null) {
                        if (dataArquivo.isAfter(hoje.plusDays(-5))) {
                            logger.info("[{}] Arquivo elegivel localizado (Data: {}). Iniciando sincronizacao...",
                                        arquivo.getFileName(), dataArquivo);
                            //processarEAtualizar(conexao, arquivo);
                            processarEAtualizarMerge(conexao, arquivo);
                            totalArquivosProcessados++;
                        } else {
                            logger.debug("[{}] Arquivo ignorado (Fora do intervalo de 5 dias atras: {}).",
                                         arquivo.getFileName(), dataArquivo);
                        }
                    } else {
                        logger.warn("[{}] Ignorado: Nao foi possível extrair a data do nome do arquivo.",
                                    arquivo.getFileName());
                    }
                }
                logger.info("Varredura concluida. Total de arquivos analisados: {}, Total de arquivos processados: {}",
                            totalArquivosEncontrados, totalArquivosProcessados);
            } catch (IOException e) {
                logger.error("Erro de I/O ao listar o diretorio de arquivos de ponto.", e);
            }

        } catch (SQLException e) {
            logger.error("Falha ao abrir conexao com o banco de dados.", e);
        }
    }

    /**
     * Executa o processamento do arquivo, inserindo registros de ponto que ainda não foram sincronizados na base e
     * atualizando suas flags que indicam se já foram sincronizados.
     *
     * @param conexao Connection com o banco de dados Oracle
     * @param arquivoOriginal Path do arquivo que será processado e atualizado
     */
    private void processarEAtualizar(Connection conexao, Path arquivoOriginal) {
        String sql = "INSERT INTO registro_ponto_pt VALUES (?,?,?,?,?)";
        String nomeArquivo = arquivoOriginal.getFileName().toString();
        
        Path arquivoProcessamento = diretorio.resolve(nomeArquivo + ".proc");
        Path arquivoAtualizado = diretorio.resolve(nomeArquivo + ".sinc");

        logger.debug("[{}] Solicitando lock para isolar arquivo...", nomeArquivo);
        lock.lock();
        
        try {
            if (!Files.exists(arquivoOriginal)) {
                logger.warn("[{}] Arquivo nao encontrado para sincronizacao.", nomeArquivo);
                return;
            }
            Files.move(arquivoOriginal, arquivoProcessamento, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.error("[{}] Falha ao isolar arquivo: {}", nomeArquivo, e.getMessage(), e);
            return;
        } finally {
            lock.unlock();
            logger.debug("[{}] Lock liberado apos isolamento.", nomeArquivo);
        }
        
        int totalNovosInseridos = 0;
        int totalJaSincronizados = 0;
        int totalLinhasLidas = 0;

        logger.info("[{}] Iniciando sincronizacao no banco de dados...", nomeArquivo);
        try (BufferedReader reader = Files.newBufferedReader(arquivoProcessamento);
             BufferedWriter writer = Files.newBufferedWriter(arquivoAtualizado);
             PreparedStatement stmt = conexao.prepareStatement(sql)) {

            String linha;
            while ((linha = reader.readLine()) != null) {
                totalLinhasLidas++;
                String linhaTrim = linha.trim();

                if (linhaTrim.length() != 36) {
                    //registrarErroLinha(nomeArquivo, totalLinhasLidas, linhaTrim);
                    continue;
                }

                if (linhaTrim.endsWith("N")) {
                    String linhaAtualizada = linhaTrim.substring(0, linhaTrim.length() - 1) + "S";

                    stmt.setString(1, config.getRelogio());
                    stmt.setString(2, linhaAtualizada);
                    stmt.setString(3, "N");
                    stmt.setString(4, "");
                    stmt.setString(5, "");

                    stmt.addBatch();
                    totalNovosInseridos++;

                    writer.write(linhaAtualizada);
                    writer.newLine();

                    if (totalNovosInseridos % 500 == 0) {
                        stmt.executeBatch();
                    }
                } else {
                    totalJaSincronizados++;
                    writer.write(linhaTrim);
                    writer.newLine();
                }
            }

            stmt.executeBatch();
            conexao.commit();

        } catch (Exception e) {
            logger.error("[{}] Erro durante a transacao. Executando rollback...", nomeArquivo, e);
            try {
                conexao.rollback();
            } catch (SQLException ex) {
                logger.error("[{}] Erro no rollback: {}", nomeArquivo, ex.getMessage(), ex);
            }
            descartarSilenciosamente(arquivoProcessamento, arquivoAtualizado);
            return;
        }

        logger.debug("[{}] Solicitando lock para reconciliar arquivo...", nomeArquivo);
        lock.lock();
        try {
            if (!Files.exists(arquivoOriginal)) {
                Files.move(arquivoAtualizado, arquivoOriginal, StandardCopyOption.ATOMIC_MOVE);
            } else {
                appendConteudo(arquivoAtualizado, arquivoOriginal);
                Files.deleteIfExists(arquivoAtualizado);
            }
            Files.deleteIfExists(arquivoProcessamento);

            logger.info("[{}] Sincronizacao concluida | Processadas: {} | Novas insercoes: {} | Ja sincronizadas: {}", 
                    nomeArquivo, totalLinhasLidas, totalNovosInseridos, totalJaSincronizados);

        } catch (IOException e) {
            logger.error("[{}] Erro ao reconciliar arquivo final: {}", nomeArquivo, e.getMessage(), e);
        } finally {
            lock.unlock();
            logger.debug("[{}] Lock liberado apos reconciliacao.", nomeArquivo);
        }
    }
    
    private void processarEAtualizarMerge(Connection conexao, Path arquivoOriginal) {
        String nomeArquivo = arquivoOriginal.getFileName().toString();
        String sqlMerge = 
            "MERGE INTO registro_ponto_pt target " +
            "USING (SELECT ? AS codg_relg, ? AS linha FROM DUAL) source " +
            "ON (target.codg_relg_regs_pnto = source.codg_relg AND target.linh_regs_regs_pnto = source.linha) " +
            "WHEN NOT MATCHED THEN " +
            "  INSERT (codg_relg_regs_pnto, linh_regs_regs_pnto, indc_aprc_regs_pnto, user_aprc_regs_pnto, data_aprc_regs_pnto) " +
            "  VALUES (source.codg_relg, source.linha, 'N', null, null)";

        try {
            logger.debug("[{}] Solicitando lock para leitura do arquivo...", nomeArquivo);
            lock.lock();

            List<String> linhasDoArquivo;

            try {
                if (!Files.exists(arquivoOriginal)) {
                    logger.warn("[{}] Arquivo nao encontrado para sincronizacao.", nomeArquivo);
                    return;
                }
                linhasDoArquivo = Files.readAllLines(arquivoOriginal);
            } catch (IOException e) {
                logger.error("[{}] Falha ao ler arquivo: {}", nomeArquivo, e.getMessage(), e);
                return;
            } finally {
                lock.unlock();
                logger.debug("[{}] Lock liberado apos leitura.", nomeArquivo);
            }
            
            int totalLinhasProcessadas = 0;

            try (PreparedStatement stmt = conexao.prepareStatement(sqlMerge)) {
                
                for (int i = 0; i < linhasDoArquivo.size(); i++) {
                    String linha = linhasDoArquivo.get(i).trim();
                    
                    
                    logger.debug("[{}] [{}] Processando registro...", nomeArquivo, linha);

                    if (linha.length() < 36) {
                        logger.error("[{}] Linha {} invalida no arquivo: '{}'", nomeArquivo, totalLinhasProcessadas + 1, linha);
                        continue;
                    }

                    stmt.setString(1, config.getRelogio());
                    stmt.setString(2, linha);

                    stmt.addBatch();
                    totalLinhasProcessadas++;

                    if (totalLinhasProcessadas % 500 == 0) {
                        stmt.executeBatch();
                    }
                }

                if (totalLinhasProcessadas % 500 != 0) {
                    stmt.executeBatch();
                }

                conexao.commit();
                logger.info("[{}] Arquivo sincronizado com sucesso. Total de registros sincronizados: {}", nomeArquivo, totalLinhasProcessadas);

            } catch (SQLException e) {
                logger.error("Erro no processamento do MERGE. Executando rollback...", e);
                conexao.rollback();
            }
        } catch (SQLException e) {
            logger.error("Erro de conexão/transação no banco de dados: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Concatena conteúdo de um arquivo em outro
     * 
     * @param origem Path de orige
     * @param destino Path de destino
     * @throws IOException
     */
    private void appendConteudo(Path origem, Path destino) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(origem);
             BufferedWriter writer = Files.newBufferedWriter(destino, java.nio.file.StandardOpenOption.APPEND)) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                writer.write(linha);
                writer.newLine();
            }
        }
    }
    
    /**
     * Descartar arquivos informados
     *
     * @param arquivos Path arquivos
     */
    private void descartarSilenciosamente(Path... arquivos) {
        for (Path p : arquivos) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {}
        }
    }

    /**
     * Retorna a conexão com a base de dados
     *
     * @return Connection Conexão com a base de dados
     * @throws SQLException Erro de conexão.
     */
    private Connection getConexao() throws SQLException {
        if (url == null) {
            throw new IllegalStateException("URL de conexao nao foi configurada antes da tentativa de abertura.");
        }
        return DriverManager.getConnection(url, usuario, senha);
    }

    /**
     * Extrair a data do nome de um arquivo de ponto.
     * Exemplo: o arquivo PTRL0001701092026.pto retorna a data 01/09/2026
     *
     * @param nomeArquivo Nome do arquivo que a data será extraída
     * @return LocalDate com a data extraída do nome do arquivo
     */
    private LocalDate extrairDataDoNome(String nomeArquivo) {
        if (nomeArquivo == null || nomeArquivo.length() < 17) {
            logger.warn("Nome do arquivo muito curto para extrair data: {}", nomeArquivo);
            return null;
        }

        String dataStr = nomeArquivo.substring(9, 17);
        try {
            return LocalDate.parse(dataStr, FORMATTER);
        } catch (DateTimeParseException e) {
            logger.warn("Formato de data invalido extraido do arquivo '{}' (padrao esperado 'ddMMyyyy'): '{}'",
                        nomeArquivo, dataStr);
        }
        return null;
    }
}
