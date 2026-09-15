import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        AnalysisCoordinator coordinator = new AnalysisCoordinator();

        System.out.println("=== Detector de Golpes ===");
        System.out.println("Digite 'sair' a qualquer momento para encerrar o programa.");

        boolean continuar = true;

        while (continuar) {
            System.out.println("");
            System.out.println("Digite a mensagem que deseja verificar:");
            String texto = scanner.nextLine();

            if (texto.equalsIgnoreCase("sair")) {
                continuar = false;
            } else {
                Message message = new Message(texto);
                AnalysisResult result = coordinator.coordinate(message);

                System.out.println("");
                if (result.isScam()) {
                    System.out.println("🚨 Resultado: POSSÍVEL GOLPE");
                } else {
                    System.out.println("✅ Resultado: Parece segura");
                }
                System.out.println("Justificativa:");
                System.out.println(result.getJustification());
            }
        }

        System.out.println("");
        System.out.println("Programa encerrado. Até logo!");
        scanner.close();
    }
}

class Message {

    private String content;

    public Message(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

class AnalysisResult {

    private boolean isScam;
    private String justification;
    private int score;

    public AnalysisResult(boolean isScam, String justification, int score) {
        this.isScam = isScam;
        this.justification = justification;
        this.score = score;
    }

    public boolean isScam() {
        return isScam;
    }

    public String getJustification() {
        return justification;
    }

    public int getScore() {
        return score;
    }
}

class SuspiciousWordsRepository {

    private List<String> suspiciousWords;

    public SuspiciousWordsRepository() {
        suspiciousWords = new ArrayList<>();
        suspiciousWords.add("clique aqui");
        suspiciousWords.add("urgente");
        suspiciousWords.add("você ganhou");
        suspiciousWords.add("prêmio");
        suspiciousWords.add("gratis");
        suspiciousWords.add("grátis");
        suspiciousWords.add("resgatar");
        suspiciousWords.add("senha");
        suspiciousWords.add("cartão de crédito");
        suspiciousWords.add("cpf");
        suspiciousWords.add("transferência");
        suspiciousWords.add("pix urgente");
        suspiciousWords.add("bloqueado");
        suspiciousWords.add("confirmar dados");
    }

    public List<String> getSuspiciousWords() {
        return suspiciousWords;
    }
}

class RuleBasedAnalyzer {

    private SuspiciousWordsRepository repository;

    public RuleBasedAnalyzer() {
        this.repository = new SuspiciousWordsRepository();
    }

    public AnalysisResult analyze(Message message) {
        String text = message.getContent().toLowerCase();
        int score = 0;
        String foundWords = "";

        for (String word : repository.getSuspiciousWords()) {
            if (text.contains(word.toLowerCase())) {
                score++;
                foundWords = foundWords + word + ", ";
            }
        }

        boolean isScam = score >= 2;
        String justification;

        if (score > 0) {
            justification = "Palavras suspeitas encontradas: " + foundWords;
        } else {
            justification = "Nenhuma palavra suspeita encontrada pelas regras locais.";
        }

        return new AnalysisResult(isScam, justification, score);
    }
}

class AiAnalyzer {

    // Substitua pela sua chave de API do Gemini (não compartilhe/comite essa chave em repositório público)
    private static final String API_KEY = "AQ.Ab8RN6KnvCPdrqRUKnspx4tYkdgyL6ePL7NHWCUdy3qR3utabQ";
    private static final String URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent";
    private static final int MAX_TENTATIVAS = 3;
    private static final int ESPERA_ENTRE_TENTATIVAS_MS = 2000;

    public AnalysisResult analyze(Message message) {
        String prompt = "Analise a mensagem abaixo e diga se e um golpe/phishing ou nao. "
                + "Responda APENAS no formato: RESULTADO: SIM ou NAO | JUSTIFICATIVA: (motivo curto em portugues).\n\n"
                + "Mensagem: \"" + message.getContent() + "\"";

        String jsonBody = "{\"contents\":[{\"parts\":[{\"text\":\"" + escapeJson(prompt) + "\"}]}]}";

        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(URL))
                        .header("Content-Type", "application/json")
                        .header("x-goog-api-key", API_KEY)
                        .POST(BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 503 || response.statusCode() == 429) {
                    if (tentativa < MAX_TENTATIVAS) {
                        Thread.sleep(ESPERA_ENTRE_TENTATIVAS_MS);
                        continue;
                    } else {
                        return new AnalysisResult(false,
                                "A IA está sobrecarregada no momento, mesmo após " + MAX_TENTATIVAS + " tentativas.", 0);
                    }
                }

                if (response.statusCode() != 200) {
                    return new AnalysisResult(false,
                            "Erro ao consultar a API de IA (status " + response.statusCode() + ").", 0);
                }

                String textoResposta = extrairTexto(response.body());
                boolean isScam = textoResposta.toUpperCase().contains("RESULTADO: SIM");

                String textoFormatado = textoResposta
                        .replace(" | JUSTIFICATIVA:", "\n\nJustificativa (IA):")
                        .replace(" | Justificativa:", "\n\nJustificativa (IA):")
                        .replace("RESULTADO:", "Resultado (IA):");

                return new AnalysisResult(isScam, textoFormatado, 0);

            } catch (Exception e) {
                if (tentativa == MAX_TENTATIVAS) {
                    return new AnalysisResult(false, "Erro ao consultar a API de IA: " + e.getMessage(), 0);
                }
            }
        }

        return new AnalysisResult(false, "Não foi possível obter resposta da IA.", 0);
    }

    private String escapeJson(String texto) {
        return texto.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String extrairTexto(String jsonResposta) {
        int inicio = jsonResposta.indexOf("\"text\": \"");
        if (inicio == -1) {
            inicio = jsonResposta.indexOf("\"text\":\"");
            if (inicio == -1) return "Não foi possível interpretar a resposta da IA.";
            inicio += 8;
        } else {
            inicio += 9;
        }
        int fim = jsonResposta.indexOf("\"", inicio);
        while (fim > 0 && jsonResposta.charAt(fim - 1) == '\\') {
            fim = jsonResposta.indexOf("\"", fim + 1);
        }
        if (fim == -1) return "Não foi possível interpretar a resposta da IA.";
        return jsonResposta.substring(inicio, fim).replace("\\n", " ").replace("\\\"", "\"");
    }
}

class AnalysisCoordinator {

    private static final int THRESHOLD = 2;

    private RuleBasedAnalyzer ruleBasedAnalyzer;
    private AiAnalyzer aiAnalyzer;

    public AnalysisCoordinator() {
        this.ruleBasedAnalyzer = new RuleBasedAnalyzer();
        this.aiAnalyzer = new AiAnalyzer();
    }

    public AnalysisResult coordinate(Message message) {
        AnalysisResult ruleResult = ruleBasedAnalyzer.analyze(message);

        if (ruleResult.getScore() >= THRESHOLD) {
            return ruleResult;
        } else {
            AnalysisResult aiResult = aiAnalyzer.analyze(message);
            String justification = "Regras locais: " + ruleResult.getJustification()
                    + "\n\n" + aiResult.getJustification();
            return new AnalysisResult(aiResult.isScam(), justification, ruleResult.getScore());
        }
    }
}