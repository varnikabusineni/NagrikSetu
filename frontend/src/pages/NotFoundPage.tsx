import { Compass } from 'lucide-react';
import { Link } from 'react-router-dom';
import { EmptyState } from '../components/Feedback';

export default function NotFoundPage() {
  return (
    <EmptyState icon={Compass} title="Page not found" action={<Link to="/" className="btn-primary">Go to dashboard</Link>}>
      The page you are looking for does not exist or may have moved.
    </EmptyState>
  );
}
