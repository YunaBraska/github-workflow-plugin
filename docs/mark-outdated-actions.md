Mark outdated actions - plan
============================

## Requirement

In a GitHub workflow, actions used can be outdated, and should be marked as such.

## Solution

It is possible to check what is the latest version of an action, by checking the releases on its repository.
We can analyze the workflow yaml to extract the actions used, and check whether they are outdated.
If they are outdated, we can add a comment to the workflow yaml, to mark them as outdated and suggest to update them.

## Implementation steps

### Daniel

- [ ] method `String getLatestActionVersion(String actionName)` that receives an action name and returns the latest
  release, make sure to cache the value for future use. (e.g., `actions/checkout` => `v3`)
- [ ] method `Boolean isActionOutdated(String actionName, String currentVersion)` that receives an action name and a
  version (e.g. `actions/checkout` and `v2`) and returns whether the version is outdated or not. (
  e.g. `actions/checkout` and `v2` => `true`)
- [ ] method `Map<String, String> getActions(String workflowYaml)` that receives a workflow yaml and returns the list of
  actions used in it with the versions used (e.g. `actions/checkout` => `v2`)
- [ ] method `Map<String, String> getOutdatedActions(String workflowYaml)` that receives a workflow yaml and returns the
  list of actions used in it that are outdated with the latest version (e.g. `actions/checkout` => `v3`)

Use GitHub graphql API to get the latest release tag of a repository:

```graphql
query {
  repository(owner:"cunla", name:"ghactions-manager") {
    latestRelease {      
      tag {
     	  name        
      }
    }
  } 
}
```

Result:
```json
{
  "data": {
    "repository": {
      "latestRelease": {
        "tag": {
          "name": "v1.15.1"
        }
      }
    }
  }
}
```

### Yuna

- [ ] UI to mark outdated actions and add an action comment to update to the latest versions.

