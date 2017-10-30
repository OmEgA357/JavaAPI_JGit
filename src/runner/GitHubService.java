package runner;

import java.io.IOException;

public class GitHubService {
	public static void main(String[] args) throws IOException {
		GitHubCommit gc = new GitHubCommit("username@example.com","password","https://github.com/OmEgA357/checkjgit");
		gc.initialise();
	}

}