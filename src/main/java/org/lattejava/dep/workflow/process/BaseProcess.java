package org.lattejava.dep.workflow.process;

import org.lattejava.dep.domain.ResolvableItem;
import org.lattejava.dep.workflow.PublishWorkflow;

/**
 * A base class that provides some helpers for handling alternatives while fetching.
 *
 * @author Brian Pontarelli
 */
public abstract class BaseProcess implements Process {
  /**
   * Handles fetching items that have alternative names.
   *
   * @param item            The resolvable item that is being fetched.
   * @param publishWorkflow The publish workflow used to store the item if it is found.
   * @return The FetchResult if the item was fetched, or null.
   * @throws ProcessFailureException If the fetch process encountered any type of error.
   */
  protected FetchResult fetchWithAlternatives(ResolvableItem item, PublishWorkflow publishWorkflow) throws ProcessFailureException {
    FetchResult result = tryFetchCandidate(item, item.item, publishWorkflow);
    if (result != null) {
      return result;
    }

    for (String alt : item.alternativeItems) {
      result = tryFetchCandidate(item, alt, publishWorkflow);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  /**
   * Handles fetching of alternative versions of an item.
   *
   * @param item            The resolvable item that is being fetched.
   * @param alternative     The alternative name of the item.
   * @param publishWorkflow The publish workflow used to store the item if it is found.
   * @return The FetchResult if the item was fetched, or null.
   * @throws ProcessFailureException If the fetch process encountered any type of error.
   */
  protected abstract FetchResult tryFetchCandidate(ResolvableItem item, String alternative, PublishWorkflow publishWorkflow)
      throws ProcessFailureException;
}
