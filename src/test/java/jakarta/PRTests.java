package jakarta;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.PagedIterable;


/**
 * Check and update the spec review checklist on a PR in a GitHub repository
 *
 * Needs the following VM options
 * --add-opens=java.base/java.net=ALL-UNNAMED
 * --add-opens=java.base/sun.net.www.protocol.https=ALL-UNNAMED
 */
public class PRTests {
    // https://github.com/scotch-io/All-Github-Emoji-Icons
    static final String OK = "- [x] :heavy_check_mark: ";
    static final String ERROR = "- [ ] :exclamation: ";
    static final String QUESITON = "- [ ] :question:";
    // The repository with the PRs in the form of owner/repository-name
    static final String REPOSITORY = "jakartaredhat/specifications";

    static final String PATTERN = "^\s*- \\[[\s|x]\\].*\r?";

    /**
     * Enum for the spec PR checkboxes
     */
    enum CheckboxItem {
        SPEC_DIR,
        SPEC_PDF,
        SPEC_HTML,
        SPEC_INDEX,
        SPEC_TCK_PR,
        SPEC_TCK_RR,
        RR_UPDATED,
        RR_GEN_IP_LOG,
        RR_EMAIL_PMC,
        RR_START_REVIEW,
        API_STAGE_REPO,
        TCK_STAGE_URL,
        CCR_URL,
        JAVADOC_DIR,
        NONE
    }

    /**
     * A class for representing a single checkbox item in the PR
     */
    static class CheckboxItemRecord {
        CheckboxItem item = CheckboxItem.NONE;
        String info = "";
        String value = "";
        boolean checked = false;

        public CheckboxItemRecord() {}
        public CheckboxItemRecord(CheckboxItem item, String info, String value, boolean checked) {
            this.item = item;
            this.info = info;
            this.value = value;
            this.checked = checked;
        }

        public String toString() {
            return String.format("CheckboxItemRecord(%s), info:%s value:%s", item, info, value);
        }
    }


    /**
     * Do a check of a specification PR on {@link PRTests#REPOSITORY}. The PR number is either passed in via args, or
     * defaults to 1. The OAUTH token to use needs to be provided via the GITHUB_TOKEN environment variable.
     *
     * @param args - provides the PR number
     * @throws IOException - on failure
     */
    public static void main(String[] args) throws IOException {
        String token = System.getenv("GITHUB_TOKEN");
        if(token == null) {
            throw new IllegalStateException("Specification the access token to use via the GITHUB_TOKEN environment variable");
        }
        int prNumber = 1;
        if(args.length > 0) {
            prNumber = Integer.parseInt(args[0]);
        }
        GitHub github = new GitHubBuilder().withOAuthToken(token).build();
        GHRepository specRepo = github.getRepository(REPOSITORY);
        GHPullRequest pr = specRepo.getPullRequest(prNumber);
        GHUser assignee = pr.getAssignee();
        System.out.printf("PR#%d(%s), changed files: %d, review comments: %d, url=%s\n", prNumber, pr.getTitle(),
                          pr.getChangedFiles(), pr.getReviewComments(), pr.getIssueUrl());
        String body = pr.getBody();
        System.out.printf("body: %s\n", body);

        List<CheckboxItemRecord> prCheckboxItems = parseCheckboxItems(body);
        System.out.printf("Parsed(%d) items\n%s\n", prCheckboxItems.size(), prCheckboxItems);


        // Print out info about PR files
        ArrayList<String> files = new ArrayList<>();
        PagedIterable<GHPullRequestFileDetail> prFiles = pr.listFiles();
        for (GHPullRequestFileDetail file : prFiles) {
            System.out.printf("file: %s/%s\nblob: %s\n, contents: %s\n", file.getFilename(), file.getStatus(),
                              file.getRawUrl(), file.getContentsUrl());
            files.add(file.getFilename());
        }

        // Separate the PR files into spec, javadoc and other files
        ArrayList<String> specFiles = new ArrayList<>();
        ArrayList<String> javadocFiles = new ArrayList<>();
        ArrayList<String> otherFiles = new ArrayList<>();

        String specPdf = null;
        String specHtml = null;
        for (String f : files) {
            if (f.indexOf("apidocs") > 0) {
                javadocFiles.add(f);
            } else if (f.matches(".*/[0-9]+/.*")) {
                specFiles.add(f);
                if(f.endsWith(".pdf")) {
                    int slash = f.lastIndexOf('/');
                    specPdf = f.substring(slash+1);
                } else if(f.endsWith(".html")) {
                    int slash = f.lastIndexOf('/');
                    specHtml = f.substring(slash+1);
                }
            } else {
                otherFiles.add(f);
            }
        }
        String specName = "unknown";
        String specVersion = "unknown";
        if (specFiles.size() > 0) {
            // e.g., coreprofile/10/jakarta-coreprofile-spec-10.html
            String[] s = specFiles.get(0).split("/");
            specName = s[0];
            specVersion = s[1];
        } else {
            //cannot determine...
            System.out.println("No spec changes found");
        }
        System.out.println("specification name: " + specName);
        System.out.println("specification version: " + specVersion);
        System.out.println("");


        System.out.println("SpecFiles:");
        specFiles.forEach(System.out::println);
        System.out.println("");

        System.out.println("JavadocFiles:");
        javadocFiles.forEach(System.out::println);
        System.out.println("");

        System.out.println("RemainingFiles:");
        otherFiles.forEach(System.out::println);
        System.out.println("");

        // Run through checks associated with step 1 of:
        // https://raw.githubusercontent.com/jakartaee/specification-committee/master/spec_review_checklist.md
        String review = "Hello, I'm here to help you checking this pull request for __" + specName + "__, version __" + specVersion + "__\n\n";

        review += "1. Spec PR\n";
        //  - [ ] PR uses [template](https://github.com/jakartaee/specifications/blob/master/pull_request_template.md)
        if(prCheckboxItems.size() == CheckboxItem.values().length-1) {
            review += OK + "PR uses [template](https://github.com/jakartaee/specifications/blob/master/pull_request_template.md)\n";
        } else {
            review += ERROR + "PR uses [template](https://github.com/jakartaee/specifications/blob/master/pull_request_template.md)\n";
        }
        // Directory of form {spec}/x.y
        if(!specName.equals("undefined") && !specVersion.equals("undefined")) {
            review += OK + "Directory of form {spec}/x.y\n";
        } else {
            review += ERROR + "Directory of form {spec}/x.y\n";
        }
        // PDF of form jakarta-{spec}-spec-x.y.pdf ("-spec" preferred but not required
        String test = "jakarta-%s-spec-%s.pdf".formatted(specName, specVersion);
        String test2 = "jakarta-%s-%s.pdf".formatted(specName, specVersion);
        System.out.printf("PDF-test: %s\n", test);
        if(specPdf == null || (!specPdf.equals(test) && !specPdf.equals(test2))) {
            review += ERROR + "PDF of form jakarta-{spec}-spec-x.y.pdf ('-spec' preferred but not required\n";
        } else {
            review += OK + "PDF of form jakarta-{spec}-spec-x.y.pdf ('-spec' preferred but not required\n";
        }
        // HTML of form jakarta-{spec}-spec-x.y.html ("-spec" preferred but not required)
        test = "jakarta-%s-spec-%s.html".formatted(specName, specVersion);
        test2 = "jakarta-%s-%s.html".formatted(specName, specVersion);
        System.out.printf("HTML-test: %s\n", test);
        if(specHtml == null || (!specHtml.equals(test) && !specHtml.equals(test2))) {
            review += ERROR + "HTML of form jakarta-{spec}-spec-x.y.html ('-spec' preferred but not required\n";
        } else {
            review += OK + " HTML of form jakarta-{spec}-spec-x.y.html ('-spec' preferred but not required\n";
        }

        //- [ ] Index page {spec}/x.y/_index.md following [template](https://github.com/jakartaee/specification-committee/blob/master/spec_page_template.md)
        review += QUESITON + "Index page {spec}/x.y/_index.md following [template](https://github.com/jakartaee/specification-committee/blob/master/spec_page_template.md)\n";
        //- [ ] Index page {spec}/_index.md following [template](https://github.com/jakartaee/specification-committee/blob/master/spec_index_template.md)
        review += QUESITON + "Index page {spec}/_index.md following [template](https://github.com/jakartaee/specification-committee/blob/master/spec_index_template.md)\n";
        //- [ ] No other files (e.g., no jakarta_ee_logo_schooner_color_stacked_default.png)
        if(otherFiles.size() == 0) {
            review += OK + "No other files\n";
        } else {
            review += ERROR + "No other files\n"+otherFiles+"\n";
        }
        //- [ ] Staging repository link of the form https://jakarta.oss.sonatype.org/content/repositories/staging/jakarta/{spec}/jakarta.{spec}-api/x.y.z/
        CheckboxItemRecord apiRepo = prCheckboxItems.get(CheckboxItem.API_STAGE_REPO.ordinal());
        if(apiRepo == null || apiRepo.value.length() == 0) {
            review += ERROR + "Staging repository link of the form https://jakarta.oss.sonatype.org/content/repositories/staging/jakarta/{spec}/jakarta.{spec}-api/x.y.z/\n";
        } else {
            String url = apiRepo.value.trim();
            try {
                URL testUrl = new URL(url);
                long length = testUrl.openConnection().getContentLength();
                if(length > 0) {
                    review += OK + "Staging repository link of the form https://jakarta.oss.sonatype.org/content/repositories/staging/jakarta/{spec}/jakarta.{spec}-api/x.y.z/\n";
                } else {
                    review += ERROR + "Staging repository link of the form https://jakarta.oss.sonatype.org/content/repositories/staging/jakarta/{spec}/jakarta.{spec}-api/x.y.z/\n";
                    review += "\tContent length was zero for: "+url+"\n";
                }
            } catch (Exception e) {
                review += ERROR + "Staging repository link of the form https://jakarta.oss.sonatype.org/content/repositories/staging/jakarta/{spec}/jakarta.{spec}-api/x.y.z/\n";
                review += "\tFailed to access URL: "+url+"\n";
            }
        }
        //- [ ] EFTL TCK link of the form http://download.eclipse.org/.../+.zip
        CheckboxItemRecord tckRepo = prCheckboxItems.get(CheckboxItem.TCK_STAGE_URL.ordinal());
        if(tckRepo == null || tckRepo.value.length() == 0) {
            review += ERROR + "EFTL TCK link of the form http://download.eclipse.org/.../+.zip\n";
        } else {
            String url = tckRepo.value.trim();
            if(!url.startsWith("http://download.eclipse.org/")) {
                review += ERROR + "EFTL TCK link of the form http://download.eclipse.org/.../+.zip\n";
                review += "\t"+url+" not under http://download.eclipse.org/\n";
            }
            try {
                URL testUrl = new URL(url);
                long length = testUrl.openConnection().getContentLength();
                if(length > 0) {
                    review += OK + "EFTL TCK link of the form http://download.eclipse.org/.../+.zip\n";
                } else {
                    review += ERROR + "EFTL TCK link of the form http://download.eclipse.org/.../+.zip\n";
                    review += "\tContent length was zero for: "+url+"\n";
                }
            } catch (Exception e) {
                review += ERROR + "EFTL TCK link of the form http://download.eclipse.org/.../+.zip\n";
                review += "\tFailed to access URL: "+url+"\n";
            }
        }

        //- [ ] Compatibility certification link of the form https://github.com/eclipse-ee4j/{project}/#{issue}
        CheckboxItemRecord ccrIssue = prCheckboxItems.get(CheckboxItem.CCR_URL.ordinal());
        if(ccrIssue == null || ccrIssue.value.length() == 0) {
            review += ERROR + "Compatibility certification link of the form https://github.com/eclipse-ee4j/{project}/#{issue}\n";
        } else {
            review += QUESITON + "Compatibility certification link of the form https://github.com/eclipse-ee4j/{project}/#{issue}\n";
        }
        //- [ ] (Optional) Second PR for just apidocs
        review += QUESITON + "(Optional) Second PR for just apidocs\n";


        // Look for comment from assigned user that starts with # Spec Review Checklist
        PagedIterable<GHIssueComment> comments = pr.listComments();
        for(GHIssueComment comment: comments) {
            System.out.printf("Comment by %s:\n%s\n", comment.getUser().getLogin(), comment.getBody());

            if(assignee.getLogin().equals(comment.getUser().getLogin())) {
                String commentBody = comment.getBody();
                if(commentBody.startsWith("# Spec Review Checklist")) {
                    System.out.printf("+++ Updating spec review checklist...\n");
                    comment.update("# Spec Review Checklist\n"+review);
                }
            }
        }
    }

    /**
     * Parse the PR body into CheckboxItemRecords
     * @param body - body of main PR comment
     * @return list of parsed records
     */
    static List<CheckboxItemRecord> parseCheckboxItems(String body) {
        System.out.println("+++ Begin parseCheckboxItems");
        // Parse the template content
        String[] lines = body.split("\n");
        ArrayList<CheckboxItemRecord> checkboxItems = new ArrayList<>();
        CheckboxItemRecord currentRecord  = new CheckboxItemRecord();
        for(String line : lines) {
            System.out.printf("%s", line);
            // Match a line begining with - [ |x]
            if(line.matches(PATTERN)) {
                // Start of a new checkbox section
                currentRecord = new CheckboxItemRecord();
                // Strip the leading - [..]
                int prefix = line.indexOf(']');
                currentRecord.info = line.substring(prefix+1);
                // Save if it was checked
                currentRecord.checked = line.indexOf("[x]") > 0;
                int itemNumber = checkboxItems.size();
                currentRecord.item = CheckboxItem.values()[itemNumber];
                checkboxItems.add(currentRecord);
                System.out.printf("\tmaps to: %s", currentRecord.item);
            } else if(checkboxItems.size() > 0 && currentRecord.value.length() == 0) {
                // This is the line(s) following the - [*]... checkbox. Only use the first line as value
                currentRecord.value = line.trim();
                System.out.printf("\tvalue for %s is: %s", currentRecord.item, currentRecord.value);
            }
        }
        System.out.println("+++ End parseCheckboxItems, count="+checkboxItems.size());

        return checkboxItems;
    }
}

