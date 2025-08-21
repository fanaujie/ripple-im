package com.fanaujie.ripple.database.mapper;

import com.fanaujie.ripple.database.config.MyBatisConfig;
import com.fanaujie.ripple.database.model.User;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = MyBatisConfig.class,
        properties = {
            "mybatis.mapper-locations=classpath:mapper/*.xml",
            "mybatis.type-aliases-package=com.fanaujie.ripple.database.model"
        })
@Testcontainers
@EnableAutoConfiguration
class UserMapperTest {

    @Container
    static MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.4.5")
                    .withDatabaseName("test_ripple")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        Flyway flyway =
                Flyway.configure()
                        .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                        .locations("classpath:db/migration")
                        .load();
        flyway.migrate();
    }

    @AfterEach
    void tearDown() {
        Flyway flyway =
                Flyway.configure()
                        .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                        .locations("classpath:db/migration")
                        .cleanDisabled(false)
                        .load();
        flyway.clean();
    }

    @Test
    void testInsertAndFindByAccount() {
        User user = new User();
        user.setAccount("testuser");
        user.setPassword("password123");
        user.setEnabled(true);
        user.setRole("ROLE_USER");

        userMapper.insertUser(user);
        assertTrue(user.getId() > 0);

        User foundUser = userMapper.findByAccount("testuser");
        assertNotNull(foundUser);
        assertEquals("testuser", foundUser.getAccount());
        assertEquals("password123", foundUser.getPassword());
        assertTrue(foundUser.isEnabled());
        assertEquals("ROLE_USER", foundUser.getRole());
        assertNotNull(foundUser.getCreatedTime());
        assertNotNull(foundUser.getUpdatedTime());
    }

    @Test
    void testFindByAccountNotFound() {
        User user = userMapper.findByAccount("nonexistent");
        assertNull(user);
    }

    @Test
    void testUpdateUser() {
        User user = new User();
        user.setAccount("updatetest");
        user.setPassword("original");
        user.setEnabled(true);
        user.setRole("ROLE_USER");

        userMapper.insertUser(user);

        User updateUser = new User();
        updateUser.setAccount("updatetest");
        updateUser.setPassword("updated");
        updateUser.setEnabled(false);
        updateUser.setRole("ROLE_ADMIN");

        userMapper.updateUser(updateUser);

        User foundUser = userMapper.findByAccount("updatetest");
        assertNotNull(foundUser);
        assertEquals("updated", foundUser.getPassword());
        assertFalse(foundUser.isEnabled());
        assertEquals("ROLE_ADMIN", foundUser.getRole());
    }

    @Test
    void testDeleteUser() {
        User user = new User();
        user.setAccount("deletetest");
        user.setPassword("password");
        user.setEnabled(true);
        user.setRole("ROLE_USER");

        userMapper.insertUser(user);
        assertEquals(1, userMapper.countByAccount("deletetest"));

        userMapper.deleteUser("deletetest");
        assertEquals(0, userMapper.countByAccount("deletetest"));
        assertNull(userMapper.findByAccount("deletetest"));
    }

    @Test
    void testCountByAccount() {
        assertEquals(0, userMapper.countByAccount("counttest"));

        User user = new User();
        user.setAccount("counttest");
        user.setPassword("password");
        user.setEnabled(true);
        user.setRole("ROLE_USER");

        userMapper.insertUser(user);
        assertEquals(1, userMapper.countByAccount("counttest"));
    }

    @Test
    void testFindUserIdByAccount() {
        User user = new User();
        user.setAccount("idtest");
        user.setPassword("password");
        user.setEnabled(true);
        user.setRole("ROLE_USER");

        userMapper.insertUser(user);
        long insertedId = user.getId();

        Long foundId = userMapper.findUserIdByAccount("idtest");
        assertNotNull(foundId);
        assertEquals(insertedId, foundId.longValue());
    }

    @Test
    void testFindUserIdByAccountNotFound() {
        Long foundId = userMapper.findUserIdByAccount("nonexistent");
        assertNull(foundId);
    }

    @Test
    void testChangePassword() {
        User user = new User();
        user.setAccount("passwordtest");
        user.setPassword("oldpassword");
        user.setEnabled(true);
        user.setRole("ROLE_USER");

        userMapper.insertUser(user);

        userMapper.changePassword("passwordtest", "newpassword");

        User foundUser = userMapper.findByAccount("passwordtest");
        assertNotNull(foundUser);
        assertEquals("newpassword", foundUser.getPassword());
    }

    @Test
    void testMultipleUsers() {
        User user1 = new User();
        user1.setAccount("user1");
        user1.setPassword("pass1");
        user1.setEnabled(true);
        user1.setRole("ROLE_USER");

        User user2 = new User();
        user2.setAccount("user2");
        user2.setPassword("pass2");
        user2.setEnabled(false);
        user2.setRole("ROLE_ADMIN");

        userMapper.insertUser(user1);
        userMapper.insertUser(user2);

        User found1 = userMapper.findByAccount("user1");
        User found2 = userMapper.findByAccount("user2");

        assertNotNull(found1);
        assertNotNull(found2);
        assertEquals("user1", found1.getAccount());
        assertEquals("user2", found2.getAccount());
        assertTrue(found1.isEnabled());
        assertFalse(found2.isEnabled());
        assertEquals("ROLE_USER", found1.getRole());
        assertEquals("ROLE_ADMIN", found2.getRole());
    }
}
