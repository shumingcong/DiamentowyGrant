/*
 * Copyright (c) 2003, 2007-8 Matteo Frigo
 * Copyright (c) 2003, 2007-8 Massachusetts Institute of Technology
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */

/* internal API definitions */
#ifndef __API_H__
#define __API_H__

#ifndef CALLING_FFTW /* defined in hook.c, when calling internal functions */
#  define COMPILING_FFTW /* used for DLL symbol exporting in fftw3.h */
#endif

/* When compiling with GNU libtool on Windows, DLL_EXPORT is #defined
   for compiling the shared-library code.  In this case, we'll #define
   FFTW_DLL to add dllexport attributes to the specified functions in
   fftw3.h.

   If we don't specify dllexport explicitly, then libtool
   automatically exports all symbols.  However, if we specify
   dllexport explicitly for any functions, then libtool apparently
   doesn't do any automatic exporting.  (Not documented, grrr, but
   this is the observed behavior with libtool 1.5.8.)  Thus, using
   this forces us to correctly dllexport every exported symbol, or
   linking bench.exe will fail.  This has the advantage of forcing
   us to mark things correctly, which is necessary for other compilers
   (such as MS VC++). */
#ifdef DLL_EXPORT
#  define FFTW_DLL
#endif

/* just in case: force <fftw3.h> not to use C99 complex numbers
   (we need this for IBM xlc because _Complex_I is treated specially
   and is defined even if <complex.h> is not included) */
#define FFTW_NO_Complex

#include "fftw3.h"
#include "../kernel/ifftw.h"

/* the API ``plan'' contains both the kernel plan and problem */
struct fftwf_plan_s {
     plan *pln;
     problem *prb;
     int sign;
};

/* shorthand */
typedef struct fftwf_plan_s apiplan;

/* complex type for internal use */
//typedef float fftwf_complex[2];

#define EXTRACT_REIM(sign, c, r, i) fftwf_extract_reim(sign, (c)[0], r, i)

#define TAINT_UNALIGNED(p, flg) TAINT(p, ((flg) & FFTW_UNALIGNED) != 0)

tensor *fftwf_mktensor_rowmajor(int rnk, const int *n,
			     const int *niphys, const int *nophys,
			     int is, int os);

tensor *fftwf_mktensor_iodims(int rank, const fftwf_iodim *dims, int is, int os);
tensor *fftwf_mktensor_iodims64(int rank, const fftwf_iodim64 *dims, int is, int os);
const int *fftwf_rdft2_pad(int rnk, const int *n, const int *nembed,
			int inplace, int cmplx, int **nfree);

int fftwf_many_kosherp(int rnk, const int *n, int howmany);
int fftwf_guru_kosherp(int rank, const fftwf_iodim *dims,
		    int howmany_rank, const fftwf_iodim *howmany_dims);
int fftwf_guru64_kosherp(int rank, const fftwf_iodim64 *dims,
		    int howmany_rank, const fftwf_iodim64 *howmany_dims);

/* Note: FFTW_EXTERN is used for "internal" functions used in tests/hook.c */

FFTW_EXTERN printer *fftwf_mkprinter_file(FILE *f);

FFTW_EXTERN planner *fftwf_the_planner(void);
void fftwf_configure_planner(planner *plnr);

void fftwf_mapflags(planner *, unsigned);

apiplan *fftwf_mkapiplan(int sign, unsigned flags, problem *prb);

#endif				/* __API_H__ */