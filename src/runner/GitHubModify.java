package runner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.util.FileSystemUtils;

public class GitHubModify {

	private Git git;
	private UsernamePasswordCredentialsProvider creds;
	private long lastPushFail = 0;
	private long pushTimeout = 15 * 60 * 1000;
	private boolean alreadyPushing = false;

	private String username;
	private String password;
	private String repoURL;

	public GitHubModify(String username, String password, String repoURL) {
		super();
		this.username = username;
		this.password = password;
		this.repoURL = repoURL;
	}

	public void initialise() {

		System.getProperties().put("proxySet", "true");
		System.getProperties().put("proxyHost", "172.25.74.10");
		System.getProperties().put("proxyPort", "2006");
		cloneRepo();
	}

	public void cloneRepo() {
		new Thread(new Runnable() {
			public void run() {
				creds = new UsernamePasswordCredentialsProvider(username, password);
				String path = System.getProperty("user.home") + File.separatorChar + "tempdir" + File.separatorChar
						+ "gitDir";
				File dir = new File(path);
				CloneCommand cc = new CloneCommand().setCredentialsProvider(creds).setDirectory(dir).setURI(repoURL);
				git = cc.call();

				try {
					final String outerTestFile = "/README.md";
					final String innerTest = "/a/inner.txt";

	    			// Modify the file
	    			FileUtils.write(new File(git.getRepository().getDirectory().getParent()+outerTestFile), "This is a modified outer file", "UTF-8");
	    			FileUtils.write(new File(git.getRepository().getDirectory().getParent()+innerTest), "This is a modified inner file", "UTF-8");
	    			System.out.println("Modified files: " + getModifiedFiles(git));
					add(new File("application.properties", ""));
					commit("application.properties committed successfully");
				} catch (IOException | NoWorkTreeException | GitAPIException e) {
					e.printStackTrace();
					deleteTempDirectory();
				}
			}
		}).start();
	}
	
	private Set<String> getModifiedFiles(Git git) throws NoWorkTreeException, GitAPIException, IOException {
		Status status = git.status().call();
		return status.getModified();
	}
	
	public void add(File f) throws IOException {
		File myfile = new File(git.getRepository().getDirectory().getParent(), f.getName());
		if (!myfile.exists() && !myfile.createNewFile()) {
			deleteTempDirectory();
			throw new IOException("Could not create file " + myfile);
		}
		try (BufferedWriter writer = Files
				.newBufferedWriter(Paths.get(git.getRepository().getDirectory().getParent() + "\\" + f.getName()))) {
			writer.write("Hello World !!" + "\nThis is" + "\ncommited" + "\nusing API");
		}

		try {
			git.add().addFilepattern(".").call();
			git.push().setCredentialsProvider(creds).call();
		} catch (NoFilepatternException | InvalidRemoteException e) {
			e.printStackTrace();
			deleteTempDirectory();
		}
	}

	public void commit(final String noteName) {
		new Thread(new Runnable() {
			public void run() {
				try {
					git.commit().setAuthor(username, "").setMessage(noteName).setAll(true).call();
					startPushing();
				} catch (NoHeadException | NoMessageException | UnmergedPathException | ConcurrentRefUpdateException
						| JGitInternalException | WrongRepositoryStateException e) {
					e.printStackTrace();
					deleteTempDirectory();
				}
			}
		}).start();
	}

	public void startPushing() {
		if (!alreadyPushing) {
			alreadyPushing = true;
			new Thread(new Runnable() {
				public void run() {
					while (true) {
						try {
							push();
							alreadyPushing = false;
							deleteTempDirectory();
							break; // if push is successful then break from while loop.
						} catch (InvalidRemoteException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							deleteTempDirectory();
						} catch (TransportException e) {
							deleteTempDirectory();
							lastPushFail = System.currentTimeMillis();
							try {
								Thread.sleep(pushTimeout - (System.currentTimeMillis() - lastPushFail));
							} catch (InterruptedException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
						} catch (GitAPIException e) {
							// TODO Auto-generated catch block
							deleteTempDirectory();
							e.printStackTrace();
						}
					}
				}
			}).start();
		} else {
			lastPushFail = 0;
		}
	}

	public void push() throws InvalidRemoteException, TransportException, GitAPIException {
		git.push().setCredentialsProvider(creds).call();
	}

	public void remove(File f) {
		try {
			git.rm().addFilepattern(f.getParentFile().getName() + "/" + f.getName()).call();
		} catch (NoFilepatternException e) {
			e.printStackTrace();
			deleteTempDirectory();
		}
	}

	public void deleteTempDirectory() {
		git.getRepository().close();
		if (!FileSystemUtils.deleteRecursively(new File(System.getProperty("user.home") + File.separatorChar + "tempdir"))) {
			System.out.println("Problem occurs when deleting the directory");
		} else {
			System.out.println("Deleted Successfully");
		}
	}
}