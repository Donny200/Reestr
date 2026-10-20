import * as Menu from '@radix-ui/react-dropdown-menu';
import { cn } from '../../lib/cn';
import { menuItemClass, menuSurfaceClass } from './menuStyles';

export const DropdownMenu = Menu.Root;
export const DropdownMenuTrigger = Menu.Trigger;

export function DropdownMenuContent({ className, align = 'end', ...props }: Menu.DropdownMenuContentProps) {
  return (
    <Menu.Portal>
      <Menu.Content align={align} sideOffset={6} collisionPadding={12} className={cn(menuSurfaceClass, 'min-w-[11rem]', className)} {...props} />
    </Menu.Portal>
  );
}

export function DropdownMenuItem({ className, destructive = false, ...props }: Menu.DropdownMenuItemProps & { destructive?: boolean }) {
  return (
    <Menu.Item
      className={cn(
        menuItemClass,
        '[&>svg]:h-4 [&>svg]:w-4 [&>svg]:text-secondary data-[highlighted]:[&>svg]:text-foreground',
        destructive && 'text-danger data-[highlighted]:bg-status-danger-bg data-[highlighted]:text-danger [&>svg]:text-danger data-[highlighted]:[&>svg]:text-danger',
        className,
      )}
      {...props}
    />
  );
}

export function DropdownMenuLabel(props: Menu.DropdownMenuLabelProps) {
  return <Menu.Label className="px-3 py-2 text-xs font-medium text-secondary" {...props} />;
}

export function DropdownMenuSeparator(props: Menu.DropdownMenuSeparatorProps) {
  return <Menu.Separator className="my-1 h-px bg-line" {...props} />;
}
