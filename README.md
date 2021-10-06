# githubactions

Not really an action at this point, rather an example of programatically updating a 'Spec Review Checklist'
comment that contains the https://raw.githubusercontent.com/jakartaee/specification-committee/master/spec_review_checklist.md
items. Right now this only addresses the step 1, Spec PR section.

This uses the GitHub REST binding for Java described at https://github-api.kohsuke.org/ with source
here https://github.com/hub4j/github-api.
