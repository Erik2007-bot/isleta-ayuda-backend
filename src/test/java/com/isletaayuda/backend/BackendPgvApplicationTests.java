package com.isletaayuda.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.config.location=classpath:persistence-test.properties")
class BackendPgvApplicationTests {

	@Test
	void contextLoads() {
	}

}
