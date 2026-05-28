package com.foodmanager.foodmanager;

import com.foodmanager.foodmanager.entity.User;
import com.foodmanager.foodmanager.repo.UserRepo;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class FoodmanagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(FoodmanagerApplication.class, args);
	}

	@Bean
	public CommandLineRunner loadTestData(UserRepo ur, PasswordEncoder pwdEncoder) {
		return args -> {
			if (ur.count() == 0) {
				User testUser = new User();
				testUser.setUsername("terrydavis");
				testUser.setEmail("terrydavis@templeos.com");
				testUser.setPassword(pwdEncoder.encode("sigmapenisballs"));
				ur.save(testUser);

				System.out.println("Test user loaded into database");
			}
		};
	}

}
