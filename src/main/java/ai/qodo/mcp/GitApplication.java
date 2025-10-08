package ai.qodo.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;

@SpringBootApplication
public class GitApplication {
    
    static {
        // Configure SSH session factory to use default SSH keys from ~/.ssh
        SshSessionFactory.setInstance(new SshdSessionFactory());
    }
    
    public static void main(String[] args) {
        SpringApplication.run(GitApplication.class, args);
    }

}
