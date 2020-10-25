package com.teste.conversorpdf;

import com.itextpdf.text.Document;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.PdfWriter;
import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
public class ConversorPdfApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConversorPdfApplication.class, args);
	}

}

@Configuration
class ConversorRouter {

	@Bean
	public RouterFunction<ServerResponse> routes(ConversorService conversorService){
		return route()
            .GET("/testaImagem/{nomeArquivo}", req -> ok().body(conversorService.generateImageFromPDF(req.pathVariable("nomeArquivo")), Imagem.class))
            .GET("/imagem", req -> ok().body(conversorService.getImagem(), Imagem.class))
            .POST("/imagemFromFront", req -> ok().body(req.bodyToMono(Imagem.class).flatMap(imagem -> conversorService.salvaImagem(imagem)), String.class))
			.POST("/mandaArquivo", req -> ok().body(req.bodyToMono(ArquivoPDF.class).flatMap(arquivoPDF -> conversorService.salvaArquivo(arquivoPDF)), String.class))
            .build();
	}
}

@Service
class ConversorService {
	private static final Logger logger = LoggerFactory.getLogger(ConversorService.class);

	public Mono<String> salvaArquivo(ArquivoPDF arquivoPDF){
		try {
			OutputStream os = new FileOutputStream(new File("src\\main\\resources\\documentos\\" + arquivoPDF.getNome() + ".pdf"));
			os.write(arquivoPDF.getArquivo());
			os.close();
			return Mono.just("Deu certo");
		} catch (Exception ex) {
			ex.printStackTrace();
			return Mono.just("Deu errado");
		}
	}

	public Mono<Imagem> generateImageFromPDF(String nomeArquivo) {
		try {
			PDDocument document = PDDocument.load(new File("src\\main\\resources\\documentos\\" + nomeArquivo + ".pdf"));
			PDFRenderer pdfRenderer = new PDFRenderer(document);
			for (int page = 0; page < document.getNumberOfPages(); ++page) {
				BufferedImage bim = pdfRenderer.renderImageWithDPI(
						page, 300, ImageType.RGB);
				ImageIOUtil.writeImage(
						bim, "src/main/resources/imagens/" + nomeArquivo + ".png", 300);
			}
			document.close();
			byte[] imagemBytes = Files.readAllBytes(Paths.get("src/main/resources/imagens/" + nomeArquivo + ".png"));
			Imagem imagem = new Imagem();
			imagem.setImagem(imagemBytes);
			imagem.setNome(nomeArquivo);
			return Mono.just(imagem);
		} catch (Exception e){
			logger.error(e.getMessage());
			e.printStackTrace();
			return Mono.empty();
		}
	}

	public Mono<String> generatePDFFromImage(String nomeArquivo) {
		try {
			PDDocument document = new PDDocument();
			InputStream in = new FileInputStream("src/main/resources/imagens/" + nomeArquivo + ".png");
			BufferedImage bimg = ImageIO.read(in);
			float width = bimg.getWidth();
			float height = bimg.getHeight();
			PDPage page = new PDPage(new PDRectangle(width, height));
			document.addPage(page);
			PDImageXObject img = PDImageXObject.createFromFile("src/main/resources/imagens/" + nomeArquivo + ".png", document);
			PDPageContentStream contentStream = new PDPageContentStream(document, page);
			contentStream.drawImage(img, 0, 0);
			contentStream.close();
			in.close();

			document.save(new File("src\\main\\resources\\documentos\\" + nomeArquivo + "Novo.pdf"));
			document.close();
			return Mono.just("Deu certo");
		}catch (Exception e){
			logger.error(e.getMessage());
			e.printStackTrace();
			return Mono.just("Deu erro");
		}
	}

	public Mono<Imagem> getImagem(){
	    try {
            byte[] imagemBytes = Files.readAllBytes(Paths.get("src/main/resources/imagens/pdf-1.png"));
            Imagem imagem = new Imagem();
            imagem.setImagem(imagemBytes);
            return Mono.just(imagem);
        } catch (Exception e){
	        e.printStackTrace();
	        return Mono.empty();
        }
    }

    public Mono<String> salvaImagem(Imagem imagem){
	    try {
            logger.info("Recebeu requisição");
            ByteArrayInputStream stream = new ByteArrayInputStream(imagem.getImagem());
            BufferedImage read = ImageIO.read(stream);
            ImageIO.write(read, "png", new File("src\\main\\resources\\imagens\\" + imagem.getNome() + "Novo.png"));

            return Mono.just("Deu certo");
        }catch (Exception e){
	        e.printStackTrace();
	        return Mono.just("Deu erro");
        }
    }
}

@Configuration
@EnableWebFlux
class CorsGlobalConfiguration implements WebFluxConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry corsRegistry) {
        corsRegistry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .maxAge(8000);
    }

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024);
    }
}

class Imagem {
	private String nome;
    private byte[] imagem;

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public byte[] getImagem() {
        return imagem;
    }

    public void setImagem(byte[] imagem) {
        this.imagem = imagem;
    }
}

class ArquivoPDF{
	private String nome;
	private byte[] arquivo;

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public byte[] getArquivo() {
		return arquivo;
	}

	public void setArquivo(byte[] arquivo) {
		this.arquivo = arquivo;
	}
}
