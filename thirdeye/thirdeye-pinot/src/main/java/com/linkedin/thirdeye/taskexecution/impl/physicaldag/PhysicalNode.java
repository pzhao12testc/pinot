package com.linkedin.thirdeye.taskexecution.impl.physicaldag;

import com.linkedin.thirdeye.taskexecution.dag.NodeIdentifier;
import com.linkedin.thirdeye.taskexecution.dataflow.reader.Reader;
import com.linkedin.thirdeye.taskexecution.impl.dataflow.InMemorySimpleReader;
import com.linkedin.thirdeye.taskexecution.impl.operator.OperatorRunner;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;

/**
 * A PhysicalNode that executes work using one partition.
 */
public class PhysicalNode extends AbstractPhysicalNode<PhysicalNode> {

  private Set<FrameworkNode> physicalNodes = new HashSet<>();

  public PhysicalNode(String name, Class operatorClass) {
    this(new NodeIdentifier(name), operatorClass);
  }

  public PhysicalNode(NodeIdentifier nodeIdentifier, Class operatorClass) {
    super(nodeIdentifier, operatorClass);
  }

  @Override
  public ExecutionStatus getExecutionStatus() {
    // Currently assume that there is only one operator runner
    if (CollectionUtils.isNotEmpty(physicalNodes)) {
      Iterator<FrameworkNode> iterator = physicalNodes.iterator();
      return iterator.next().getExecutionStatus();
    }
    return ExecutionStatus.SKIPPED;
  }

  @Override
  public Reader getOutputReader() {
    if (CollectionUtils.isNotEmpty(physicalNodes)) {
      if (physicalNodes.size() == 1) {
        FrameworkNode physicalNode = (FrameworkNode) CollectionUtils.get(physicalNodes, 0);
        return physicalNode.getOutputReader();
      } else {
        throw new IllegalArgumentException("Multiple partitions are not supported yet.");
      }
    }
    return new InMemorySimpleReader<>();
  }

  @Override
  public NodeIdentifier call() throws Exception {
    OperatorRunner runner = new OperatorRunner(nodeIdentifier, nodeConfig, operatorClass);
    physicalNodes.add(runner);

    for (FrameworkNode pNode : this.getIncomingNodes()) {
      // TODO: Get output (writer) from parents and construct inputs (readers)

      // TODO: Add node identifier to port identifier mapping
      runner.addInput(pNode.getIdentifier(), pNode.getOutputReader());
    }

    return runner.call();
  }

  @Override
  public PhysicalNode getLogicalNode() {
    return null;
  }

  @Override
  public Collection<FrameworkNode> getPhysicalNode() {
    return physicalNodes;
  }
}
