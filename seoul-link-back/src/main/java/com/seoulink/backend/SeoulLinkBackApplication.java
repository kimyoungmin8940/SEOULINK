package com.seoulink.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SEOULINK 백엔드 애플리케이션의 시작점이다.
 *
 * <p>{@link SpringBootApplication}은 자동 설정, 컴포넌트 스캔,
 * 설정 클래스 등록 기능을 한 번에 활성화한다. 이 클래스가
 * {@code com.seoulink.backend} 최상위 패키지에 있으므로 하위의
 * {@code global}, {@code domain} 패키지가 모두 스캔 대상에 포함된다.</p>
 */
@SpringBootApplication
public class SeoulLinkBackApplication {

    /**
     * Spring Boot 애플리케이션을 실행한다.
     *
     * @param args 실행 시 전달되는 명령행 인자
     */
    public static void main(String[] args) {
        SpringApplication.run(SeoulLinkBackApplication.class, args);
    }
}
