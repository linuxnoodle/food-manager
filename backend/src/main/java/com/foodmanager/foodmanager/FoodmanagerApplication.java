package com.foodmanager.foodmanager;

import com.foodmanager.foodmanager.config.AlreadyRunningGuard;
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
		SpringApplication app = new SpringApplication(FoodmanagerApplication.class);
		// bail out gracefully if another instance is already on the server port
		// (h2 would otherwise stack-trace on its file lock)
		app.addListeners(new AlreadyRunningGuard());
		app.run(args);
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
