import { Injectable } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { QrLabelPdfService } from './qr-label-pdf.service';

describe('QrLabelPdfService', () => {
  let service: QrLabelPdfService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(QrLabelPdfService);
  });

  it('builds public rules URL from token', () => {
    expect(service.buildMasPublicRulesUrl('abcToken', 'https://app.example.com')).toBe(
      'https://app.example.com/public/r/abcToken'
    );
  });

  it('places first and last labels at expected mm on a full sheet', () => {
    const slots = Array.from({ length: 48 }, (_, i) => ({
      url: `https://app.example.com/public/r/t${i}`,
      caption: `MAS-${i}`
    }));
    const { first, last } = service.computeExtents(slots, { firstSlot: 1 });
    expect(first).toEqual({ x: 10, y: 21.5, page: 1 });
    expect(last).toEqual({
      x: 10 + 5 * 32,
      y: 21.5 + 7 * 32,
      page: 1
    });
  });

  it('honours firstSlot offset on the sheet', () => {
    const slots = [{ url: 'https://app.example.com/public/r/x', caption: 'A' }];
    const { first } = service.computeExtents(slots, { firstSlot: 3 });
    // slot 3 = index 2 → col 2, row 0
    expect(first).toEqual({ x: 10 + 2 * 32, y: 21.5, page: 1 });
  });
});
