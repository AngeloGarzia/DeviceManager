/** Accept HTML pour PDF ou capture image (règle métier commune). */
export const PDF_OR_IMAGE_ACCEPT =
  'application/pdf,.pdf,image/*,.jpg,.jpeg,.png,.webp,.gif';

/** Indique si le fichier est un PDF (par type MIME ou extension). */
export function isPdfFile(file: File): boolean {
  const name = (file.name || '').toLowerCase();
  return file.type === 'application/pdf' || name.endsWith('.pdf');
}

/** Indique si le fichier est une image acceptée. */
export function isImageFile(file: File): boolean {
  const name = (file.name || '').toLowerCase();
  return (
    file.type.startsWith('image/') ||
    /\.(png|jpe?g|webp|gif)$/.test(name)
  );
}

/** PDF ou image — sinon message d'erreur métier. */
export function isPdfOrImageFile(file: File): boolean {
  return isPdfFile(file) || isImageFile(file);
}
