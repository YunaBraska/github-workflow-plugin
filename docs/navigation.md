Here is a short Navigation:

## General:

- Test Pipeline: Test Pipeline as the Tests itself are broken, JetBrains Test System cant work with async tasks out of
  the box.
- Plugin Complexity: This i want to reduce! I am really sorry about the Plugin complexity, when i started it was much
  easier but then is was also unstable as hell.
    - No own objects & No Constants - I try to keep the number of own objects low, as i have seen memory leaks while
      using custom objects! Looks like i always need to clean them up by myself for every Context like: open & close
      Project, PsiElement…
    - JetBrains does not like to have Read, Write, IO, Network traffic, Syntax Highlighting,… in the same thread. Thats
      why i often need things like this:
        - `ApplicationManager.getApplication().executeOnPooledThread`
        - `ApplicationManager.getApplication().invokeLater`
        - `ApplicationManager.getApplication().isUnitTestMode()`
        - `ApplicationManager.getApplication().runReadAction`

## Package:  [Services](https://github.com/YunaBraska/github-workflow-plugin/tree/main/src/main/java/com/github/yunabraska/githubworkflow/services):

These are the trigger and entry place to start looking at. These are the Extensions which are registered in
the  [Plugin.xml](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/resources/META-INF/plugin.xml)

- [CodeCompletion](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/java/com/github/yunabraska/githubworkflow/services/CodeCompletion.java)  -
  completes code. Its one of my first classes.
- [FileIconProvider](https://github.com/YunaBraska/github-workflow-plugin/blobmain/src/main/java/com/github/yunabraska/githubworkflow/services/FileIconProvider.java)  -
  Marks the files with an GitHub Icon
- [GitHubActionCache](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/java/com/github/yunabraska/githubworkflow/services/GitHubActionCache.java)  -
  This might be interesting for you, as this is the core logic to have a GitHub Actions and Workflow cache over all
  Projects
    - Careful, i did only manage to store java maps, everything else is pretty hard to  `serialize`  and  `deserialize`

- [HighlightAnnotator](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/java/com/github/yunabraska/githubworkflow/services/HighlightAnnotator.java)  -
  Adds Syntax Highlighting and text formats for the Reference Contributor (It receives PsiElements which are in your
  View or have been changed)
- [ReferenceContributor](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/java/com/github/yunabraska/githubworkflow/services/ReferenceContributor.java)  -
  Adds References inline or external links to  `Actions`,  `Workflows`  and now also on  `Needs`  (It receives
  PsiElements which are in your View or have been changed)
- [PluginErrorReportSubmitter](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/java/com/github/yunabraska/githubworkflow/services/PluginErrorReportSubmitter.java)  -
  Users can submit an issue on GitHub on any Exception -  _(pretty simple and clear. We don’t need actions here)_
- [ProjectStartup](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/java/com/github/yunabraska/githubworkflow/services/ProjectStartup.java)  -
  as it already tells, its the executor on Project Startup
- [SchemaProvider](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/java/com/github/yunabraska/githubworkflow/services/SchemaProvider.java)  -
  It provides several GitHub YAML Schemas -  _(pretty simple and clear. We don’t need actions here)_

## Package:  [Logic](https://github.com/YunaBraska/github-workflow-plugin/tree/main/src/main/java/com/github/yunabraska/githubworkflow/logic):

a doubtful attempt to move some common PsiElements extraction to named classes and hopefully get a faster overview whats
going on.

- You will find the logic for Syntax Highlighting, Reference Contributor, Code Completion for each logical element
  like  `Action`,  `Envs`,  `GitHub`,  `Inputs`,  `Jobs`,…

## Package:  [Helper](https://github.com/YunaBraska/github-workflow-plugin/tree/main/src/main/java/com/github/yunabraska/githubworkflow/helper):

Boring helper / utils classes

- [PsiElementHelper](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/java/com/github/yunabraska/githubworkflow/helper/PsiElementHelper.java)  -
  A core logic to navigate through the PsiElements
- [GitHubWorkflowConfig](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/java/com/github/yunabraska/githubworkflow/helper/GitHubWorkflowConfig.java)  -
  A core config, mostly about the descriptions of the PsiElements

## My next Plans:

- Creation of a  `PreProcessor`  to have only one place to parse PsiElements:
    - Why:
      The  [HighlightAnnotator](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/java/com/github/yunabraska/githubworkflow/services/HighlightAnnotator.java)
      and  [ReferenceContributor](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/java/com/github/yunabraska/githubworkflow/services/ReferenceContributor.java)
      are doing mostly the same operations
      and  [CodeCompletion](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/java/com/github/yunabraska/githubworkflow/services/CodeCompletion.java)
      is also doing partially the same things. This leads to duplicated code, complexity and context issues like i
      mentioned
      about same thread with different contexts (Read, Write, IO, Network traffic, Syntax Highlighting) which JetBrains
      doesn’t like
        - Cache: I would prefer to use the Build in cache for PsiElements
          like:  `PsiElement.getUserData()`  &  `PsiElement.putUserData()`  This cache is managed by jetBrains and can
          be
          easily
          picked up
          by  [HighlightAnnotator](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/java/com/github/yunabraska/githubworkflow/services/HighlightAnnotator.java)  &  [ReferenceContributor](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/java/com/github/yunabraska/githubworkflow/services/ReferenceContributor.java)  &  [CodeCompletion](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/java/com/github/yunabraska/githubworkflow/services/CodeCompletion.java)
    -
  Trigger  [HighlightAnnotator](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/java/com/github/yunabraska/githubworkflow/services/HighlightAnnotator.java)  &  [ReferenceContributor](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/java/com/github/yunabraska/githubworkflow/services/ReferenceContributor.java):
  after the  `PreProcessor`  is done. Currently i only know how to trigger Syntax
  Highlighting:  [triggerSyntaxHighlightingForActiveFiles()](https://github.com/YunaBraska/github-workflow-plugin/blob/main/src/main/java/com/github/yunabraska/githubworkflow/services/GitHubActionCache.java)
    - Trigger  `Preprocessor`  after FileChange: this should be simple and there should be a fixed delay for e.g. 5
      seconds,
      so that we are not spamming the  `PreProcessor`  after every typing.


## My Questions:
- Are you willing to migrate to Kotlin?
- Are you willing to consider 
