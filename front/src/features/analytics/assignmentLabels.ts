import type { AssignmentState } from '../../types/analytics';
import type { Translate } from '../../utils/errors';

export const assignmentStateLabels: Record<AssignmentState, string> = {
  ASSIGNED: 'Assigned',
  UNASSIGNED: 'Unassigned',
  NEEDS_REASSIGNMENT: 'Needs reassignment',
  UNKNOWN: 'Could not check',
};

export function assignmentStateLabel(state: AssignmentState, t: Translate): string {
  return t(`assignments.state.${state}`, assignmentStateLabels[state]);
}
